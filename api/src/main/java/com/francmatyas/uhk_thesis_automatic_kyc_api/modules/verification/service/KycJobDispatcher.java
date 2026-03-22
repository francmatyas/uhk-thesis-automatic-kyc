package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.amqp.AmqpConfig;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJob;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.model.WorkerJobStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.repository.WorkerJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Odesílá úlohy KYC verifikace do Python workeru přes RabbitMQ.
 *
 * <p>Worker naslouchá frontě {@code q.worker.kyc_worker}, která je navázaná na
 * exchange {@code x.jobs} s routing key {@code jobs.kyc_worker.#}.
 * Jednotlivé typy úloh (např. {@code verify_czech_id}) jsou vložené do těla zprávy,
 * takže worker může interně směrovat zpracování.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycJobDispatcher {

    private static final String WORKER_TYPE = "kyc_worker";

    private final RabbitTemplate rabbit;
    private final WorkerJobRepository workerJobRepository;
    private final ObjectMapper mapper;

    @Value("${api.instanceId:api-1}")
    private String apiInstanceId;

    /**
     * Odesílá všechny automatické kontroly potřebné pro danou verifikaci.
     * Každá kontrola je samostatný WorkerJob, aby se výsledky sledovaly odděleně.
     */
    public void dispatchChecks(Verification verification) {
        // Určení kontrol podle konfigurace journey template.
        // Zatím odesíláme všechny standardní kontroly; volající je může později filtrovat přes config_json.
        dispatchJob(verification, "aml_screen", buildAmlPayload(verification));
        // Dokumentové a biometrické kontroly se odesílají až po nahrání dokumentů klientem.
        // Spouští se zvlášť přes dispatchDocumentCheck() / dispatchBiometricCheck().
    }

    public WorkerJob dispatchDocumentCheck(Verification verification, String jobType,
                                           String frontImagePath, String backImagePath) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("verificationId", verification.getId().toString());
        if (frontImagePath != null) payload.put("frontImagePath", frontImagePath);
        if (backImagePath != null) payload.put("backImagePath", backImagePath);
        return dispatchJob(verification, jobType, payload);
    }

    public WorkerJob dispatchFaceMatch(Verification verification,
                                       String documentPath, String selfiePath) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("verificationId", verification.getId().toString());
        payload.put("documentPath", documentPath);
        payload.put("selfiePath", selfiePath);
        return dispatchJob(verification, "compare_faces", payload);
    }

    public WorkerJob dispatchLiveness(Verification verification, java.util.List<String> imagePaths) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("verificationId", verification.getId().toString());
        var arr = payload.putArray("imagePaths");
        imagePaths.forEach(arr::add);
        return dispatchJob(verification, "liveness_check", payload);
    }

    // ------------------------------------------------------------------

    private ObjectNode buildAmlPayload(Verification verification) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("verificationId", verification.getId().toString());
        var ci = verification.getClientIdentity();
        if (ci != null) {
            String fullName = (ci.getFirstName() != null ? ci.getFirstName() : "")
                    + " " + (ci.getLastName() != null ? ci.getLastName() : "");
            payload.put("fullName", fullName.trim());
            if (ci.getDateOfBirth() != null) {
                payload.put("dob", ci.getDateOfBirth());
            }
        }
        return payload;
    }

    private WorkerJob dispatchJob(Verification verification, String jobType, Object payload) {
        WorkerJob job = new WorkerJob();
        job.setType(jobType);
        job.setStatus(WorkerJobStatus.QUEUED);
        job.setPayload(mapper.valueToTree(payload));
        job.setApiInstanceId(apiInstanceId);
        job.setAttempt(1);
        job.setMaxAttempts(3);
        job.setTimeoutMs(300_000L);
        job.setQueuedAt(Instant.now());
        workerJobRepository.saveAndFlush(job);

        String routingKey = "jobs." + WORKER_TYPE + "." + jobType;
        var body = Map.of(
                "jobId", job.getId().toString(),
                "type", jobType,
                "version", 1,
                "payload", payload,
                "timeoutMs", job.getTimeoutMs(),
                "requestedAt", Instant.now().toString(),
                "attempt", job.getAttempt()
        );

        try {
            rabbit.convertAndSend(AmqpConfig.X_JOBS, routingKey, body, msg -> {
                msg.getMessageProperties().setCorrelationId(job.getId().toString());
                msg.getMessageProperties().setHeader("x-reply-to", "results.api." + apiInstanceId);
                msg.getMessageProperties().setHeader("x-api-instance", apiInstanceId);
                msg.getMessageProperties().setHeader("x-verification-id", verification.getId().toString());
                return msg;
            });
        } catch (Exception e) {
            log.error("Failed to enqueue KYC job {} for verification {}", jobType, verification.getId(), e);
            job.setStatus(WorkerJobStatus.FAILED);
            job.setFinishedAt(Instant.now());
            workerJobRepository.save(job);
            throw e;
        }

        log.info("Dispatched KYC job type={} jobId={} verificationId={}",
                jobType, job.getId(), verification.getId());
        return job;
    }
}
