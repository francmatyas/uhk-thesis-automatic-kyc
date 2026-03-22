package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.amqp.AmqpConfig;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.KycResultHandler;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.dto.CreateWorkerJobRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.dto.WorkerJobResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJob;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJobStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.repository.WorkerJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerJobService {
    private final WorkerJobRepository workerJobRepository;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final String apiInstanceId = System.getenv().getOrDefault("API_INSTANCE_ID", "api-1");

    @Autowired
    @Lazy
    private KycResultHandler kycResultHandler;

    /* ===================== CREATE & ENQUEUE ===================== */
    @Transactional
    public WorkerJobResponse createAndEnqueue(CreateWorkerJobRequest req) {
        // Idempotence (volitelné)
        if (req.idempotencyKey() != null) {
            Optional<WorkerJob> existing = workerJobRepository.findByIdempotencyKey(req.idempotencyKey());
            if (existing.isPresent()) return toDto(existing.get());
        }

        WorkerJob job = new WorkerJob();
        job.setType(req.type());
        job.setStatus(WorkerJobStatus.QUEUED);
        job.setPayload(req.payload());
        job.setPriority(req.priority() == null ? 0 : req.priority());
        job.setTimeoutMs(req.timeoutMs() == null ? 3_600_000L : req.timeoutMs());
        job.setIdempotencyKey(req.idempotencyKey());
        job.setAttempt(1);
        job.setMaxAttempts(3);
        job.setApiInstanceId(apiInstanceId);
        job.setQueuedAt(Instant.now());

        workerJobRepository.saveAndFlush(job); // nejdřív uložit; při selhání publish je úloha stále viditelná

        // Odeslání command zprávy
        var body = Map.of(
                "jobId", job.getId().toString(),
                "type", job.getType(),
                "version", 1,
                "payload", job.getPayload(),
                "timeoutMs", job.getTimeoutMs(),
                "requestedAt", Instant.now().toString(),
                "attempt", job.getAttempt(),
                "idempotencyKey", job.getIdempotencyKey()
        );
        String rk = "jobs." + job.getType() + ".created";

        try {
            rabbit.convertAndSend(
                    AmqpConfig.X_JOBS, rk, body,
                    msg -> {
                        msg.getMessageProperties().setCorrelationId(job.getId().toString());
                        msg.getMessageProperties().setPriority(job.getPriority());
                        msg.getMessageProperties().setHeader("x-reply-to", "results.api." + apiInstanceId);
                        msg.getMessageProperties().setHeader("x-api-instance", apiInstanceId);
                        return msg;
                    }
            );
        } catch (Exception e) {
            // označit jako FAILED:publish_error (aby to bylo vidět v UI)
            job.setStatus(WorkerJobStatus.FAILED);
            job.setError(json(
                    "code", "PUBLISH_ERROR",
                    "message", "Failed to enqueue to RabbitMQ",
                    "detail", e.getMessage()
            ));
            job.setFinishedAt(Instant.now());
            workerJobRepository.save(job);
            throw e;
        }

        return toDto(job);
    }
    /* ===================== CANCEL ===================== */

    @Transactional
    public void requestCancel(UUID jobId, String reason) {
        WorkerJob job = workerJobRepository.findActiveForCancel(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not active or not found"));

        // přechod do CANCELLING a uložení
        if (job.getStatus() != WorkerJobStatus.CANCELLING) {
            job.setStatus(WorkerJobStatus.CANCELLING);
            job.setCancelRequestedAt(Instant.now());
            workerJobRepository.save(job);
        }

        // odeslat control event
        var payload = Map.of("jobId", jobId.toString(), "reason", reason == null ? "user_request" : reason);
        rabbit.convertAndSend(AmqpConfig.X_CONTROL, "cancel.job." + jobId, payload);
    }

    /* ===================== RESULT HANDLING ===================== */

    @Transactional
    public void handleResultEvent(Map<String, Object> event, String routingKey) {
        String workerId = (String) event.get("workerId");
        UUID jobId = UUID.fromString((String) event.get("jobId"));
        String status = (String) event.get("status");

        WorkerJob job = workerJobRepository.findById(jobId).orElse(null);
        if (job == null) return; // nothing to do

        switch (status) {
            case "progress" -> {
                var progress = (Map<String, Object>) event.get("progress");
                Integer pct = progress == null ? null : (Integer) progress.get("pct");
                String msg = progress == null ? null : (String) progress.get("msg");

                if (job.getStatus() == WorkerJobStatus.QUEUED) {
                    job.setStatus(WorkerJobStatus.RUNNING);
                    job.setStartedAt(Instant.now());
                }
                job.setLastHeartbeatAt(Instant.now());
                if (pct != null) job.setProgressPct(Math.max(0, Math.min(100, pct)));
                if (msg != null) job.setProgressMsg(msg);
                if (workerId != null) job.setWorkerId(workerId);
                workerJobRepository.save(job);
            }
            case "succeeded" -> {
                job.setStatus(WorkerJobStatus.SUCCEEDED);
                job.setProgressPct(100);
                Object resultObj = event.get("result");
                job.setResult(resultObj != null ? mapper.valueToTree(resultObj) : null);
                job.setFinishedAt(Instant.now());
                workerJobRepository.save(job);
            }
            case "failed" -> {
                job.setStatus(WorkerJobStatus.FAILED);
                Object errorObj = event.get("error");
                job.setError(errorObj != null ? mapper.valueToTree(errorObj) : null);
                job.setFinishedAt(Instant.now());
                workerJobRepository.save(job);
            }
            case "cancelled" -> {
                job.setStatus(WorkerJobStatus.CANCELLED);
                Object errorObj = event.get("error");
                job.setError(errorObj != null ? mapper.valueToTree(errorObj) : null);
                job.setCancelledAt(Instant.now());
                job.setFinishedAt(Instant.now());
                workerJobRepository.save(job);
            }
            default -> { /* ignore */ }
        }

        // Zpracování výsledků KYC kontrol
        if (kycResultHandler != null && kycResultHandler.supports(job.getType())) {
            // Připojit typ úlohy do eventu, aby ho KycResultHandler mohl namapovat na CheckType
            var enriched = new java.util.HashMap<>(event);
            enriched.put("jobType", job.getType());
            kycResultHandler.handle(enriched, status, job.getPayload());
        }
    }

    /* ===================== HELPERS ===================== */

    private static WorkerJobResponse toDto(WorkerJob j) {
        return new WorkerJobResponse(
                j.getId(), j.getType(), j.getStatus(), j.getProgressPct(), j.getProgressMsg(),
                j.getResult(), j.getError(), j.getAttempt(), j.getMaxAttempts(),
                j.getWorkerId(), j.getQueuedAt(), j.getStartedAt(), j.getFinishedAt()
        );
    }

    // rychlý JSON builder přes Jackson converter v Rabbit konfiguraci (nebo přes ObjectMapper)
    private JsonNode json(Object... kv) {
        var node = mapper.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            node.set(k, mapper.valueToTree(v));
        }
        return node;
    }
}
