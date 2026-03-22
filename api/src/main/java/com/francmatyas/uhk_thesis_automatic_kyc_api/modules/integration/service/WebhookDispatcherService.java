package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryAttempt;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryJob;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpoint;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookDeliveryAttemptRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookDeliveryJobRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WebhookDispatcherService {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private static final int MAX_ERROR_LEN = 4000;
    private static final int MAX_RESPONSE_LEN = 4000;
    private static final List<WebhookDeliveryStatus> DISPATCHABLE_STATUSES = List.of(
            WebhookDeliveryStatus.PENDING,
            WebhookDeliveryStatus.RETRY_SCHEDULED
    );

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository;
    private final WebhookDeliveryJobRepository webhookDeliveryJobRepository;
    private final WebhookDeliveryAttemptRepository webhookDeliveryAttemptRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.webhooks.dispatcher.enabled:true}")
    private boolean enabled;

    @Value("${app.webhooks.dispatcher.batch-size:25}")
    private int batchSize;

    @Value("${app.webhooks.dispatcher.max-attempts:6}")
    private int maxAttempts;

    @Value("${app.webhooks.dispatcher.base-delay-ms:1000}")
    private long baseDelayMs;

    @Value("${app.webhooks.dispatcher.max-delay-ms:60000}")
    private long maxDelayMs;

    @Value("${app.webhooks.dispatcher.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    @Value("${app.webhooks.dispatcher.request-timeout-ms:10000}")
    private long requestTimeoutMs;

    private HttpClient httpClient;

    @PostConstruct
    void initHttpClient() {
        long connectMs = Math.max(250L, connectTimeoutMs);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
    }

    @Transactional
    public int enqueueTenantEvent(UUID tenantId,
                                  String eventType,
                                  Object payload,
                                  UUID correlationId,
                                  String requestId) {
        if (!enabled) {
            return 0;
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        String normalizedEventType = trimToNull(eventType);
        if (normalizedEventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        WebhookEventType resolvedEventType = WebhookEventType.fromValue(normalizedEventType)
                .orElseThrow(() -> new IllegalArgumentException("unsupported_event_type"));
        final String finalEventType = resolvedEventType.eventName();

        JsonNode payloadNode = payload == null
                ? objectMapper.createObjectNode()
                : (payload instanceof JsonNode node ? node : objectMapper.valueToTree(payload));

        List<WebhookEndpoint> endpoints = webhookEndpointSubscriptionRepository.findActiveEndpointsByTenantIdAndEventType(
                tenantId,
                resolvedEventType
        );
        if (endpoints.isEmpty()) {
            return 0;
        }

        int resolvedMaxAttempts = Math.max(1, maxAttempts);
        Instant now = Instant.now();
        String normalizedRequestId = trimToMax(trimToNull(requestId), 128);

        List<WebhookDeliveryJob> jobs = endpoints.stream()
                .map(endpoint -> WebhookDeliveryJob.builder()
                        .tenantId(tenantId)
                        .endpointId(endpoint.getId())
                        .eventType(finalEventType)
                        .eventPayload(payloadNode)
                        .status(WebhookDeliveryStatus.PENDING)
                        .attemptCount(0)
                        .maxAttempts(resolvedMaxAttempts)
                        .nextAttemptAt(now)
                        .correlationId(correlationId)
                        .requestId(normalizedRequestId)
                        .build())
                .toList();

        webhookDeliveryJobRepository.saveAll(jobs);
        return jobs.size();
    }

    @Transactional
    public int processDueDeliveries() {
        if (!enabled) {
            return 0;
        }

        int limit = Math.max(1, Math.min(batchSize, 200));
        PageRequest pageRequest = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.ASC, "nextAttemptAt").and(Sort.by(Sort.Direction.ASC, "createdAt"))
        );
        Instant now = Instant.now();
        List<UUID> dueIds = webhookDeliveryJobRepository
                .findByStatusInAndNextAttemptAtLessThanEqual(DISPATCHABLE_STATUSES, now, pageRequest)
                .stream()
                .map(WebhookDeliveryJob::getId)
                .toList();

        int processed = 0;
        for (UUID id : dueIds) {
            if (dispatchJob(id)) {
                processed++;
            }
        }
        return processed;
    }

    @Transactional
    public boolean dispatchJob(UUID jobId) {
        if (!enabled || jobId == null) {
            return false;
        }

        WebhookDeliveryJob job = webhookDeliveryJobRepository.findByIdForDispatch(jobId).orElse(null);
        if (job == null) {
            return false;
        }

        Instant now = Instant.now();
        if (!DISPATCHABLE_STATUSES.contains(job.getStatus())) {
            return false;
        }
        if (job.getNextAttemptAt() == null || job.getNextAttemptAt().isAfter(now)) {
            return false;
        }

        int attemptNo = job.getAttemptCount() + 1;
        job.setAttemptCount(attemptNo);
        job.setStatus(WebhookDeliveryStatus.IN_PROGRESS);
        job.setLastAttemptAt(now);
        webhookDeliveryJobRepository.save(job);

        WebhookEndpoint endpoint = webhookEndpointRepository.findById(job.getEndpointId()).orElse(null);
        DeliveryOutcome outcome;
        if (endpoint == null || endpoint.getStatus() != WebhookEndpointStatus.ACTIVE) {
            outcome = DeliveryOutcome.failed(
                    false,
                    null,
                    "webhook_endpoint_not_active",
                    null,
                    now,
                    Instant.now()
            );
        } else {
            outcome = deliver(endpoint, job, attemptNo, now);
        }

        saveAttempt(job, attemptNo, outcome);
        applyOutcome(job, endpoint, attemptNo, now, outcome);
        webhookDeliveryJobRepository.save(job);
        return true;
    }

    private void saveAttempt(WebhookDeliveryJob job, int attemptNo, DeliveryOutcome outcome) {
        long durationMs = Math.max(0L, Duration.between(outcome.requestedAt(), outcome.completedAt()).toMillis());
        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
                .deliveryJobId(job.getId())
                .attemptNo(attemptNo)
                .requestedAt(outcome.requestedAt())
                .completedAt(outcome.completedAt())
                .durationMs(durationMs)
                .statusCode(outcome.statusCode())
                .success(outcome.success())
                .errorMessage(trimToMax(outcome.errorMessage(), MAX_ERROR_LEN))
                .responseBody(trimToMax(outcome.responseBody(), MAX_RESPONSE_LEN))
                .build();
        webhookDeliveryAttemptRepository.save(attempt);
    }

    private void applyOutcome(WebhookDeliveryJob job,
                              WebhookEndpoint endpoint,
                              int attemptNo,
                              Instant now,
                              DeliveryOutcome outcome) {
        job.setLastStatusCode(outcome.statusCode());
        job.setLastError(trimToMax(outcome.errorMessage(), MAX_ERROR_LEN));

        if (outcome.success()) {
            job.setStatus(WebhookDeliveryStatus.SUCCEEDED);
            job.setNextAttemptAt(null);
            if (endpoint != null) {
                endpoint.setLastDeliveryAt(now);
                webhookEndpointRepository.save(endpoint);
            }
            return;
        }

        if (outcome.retryable() && attemptNo < job.getMaxAttempts()) {
            long backoffMs = computeBackoffMs(attemptNo);
            job.setStatus(WebhookDeliveryStatus.RETRY_SCHEDULED);
            job.setNextAttemptAt(now.plusMillis(backoffMs));
            return;
        }

        job.setStatus(WebhookDeliveryStatus.FAILED);
        job.setNextAttemptAt(null);
    }

    private DeliveryOutcome deliver(WebhookEndpoint endpoint,
                                    WebhookDeliveryJob job,
                                    int attemptNo,
                                    Instant requestedAt) {
        Instant completedAt;
        try {
            assertUrlNotPrivateAddress(endpoint.getUrl());
            String payloadJson = buildEnvelopeJson(job, attemptNo);
            String timestamp = String.valueOf(requestedAt.getEpochSecond());
            String signature = "v1=" + sign(endpoint.getSecret(), timestamp + "." + payloadJson);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint.getUrl()))
                    .timeout(Duration.ofMillis(Math.max(500L, requestTimeoutMs)))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "automatic-kyc-webhook-dispatcher/1.0")
                    .header("X-AutomaticKyc-Event", job.getEventType())
                    .header("X-AutomaticKyc-Delivery-Id", job.getId().toString())
                    .header("X-AutomaticKyc-Attempt", String.valueOf(attemptNo))
                    .header("X-AutomaticKyc-Timestamp", timestamp)
                    .header("X-AutomaticKyc-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));

            if (job.getCorrelationId() != null) {
                builder.header("X-Correlation-Id", job.getCorrelationId().toString());
            }
            if (job.getRequestId() != null) {
                builder.header("X-Request-Id", job.getRequestId());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            completedAt = Instant.now();

            int statusCode = response.statusCode();
            boolean success = statusCode >= 200 && statusCode < 300;
            String responseBody = trimToMax(trimToNull(response.body()), MAX_RESPONSE_LEN);
            if (success) {
                return DeliveryOutcome.success(statusCode, responseBody, requestedAt, completedAt);
            }
            return DeliveryOutcome.failed(
                    isRetryableStatus(statusCode),
                    statusCode,
                    "http_" + statusCode,
                    responseBody,
                    requestedAt,
                    completedAt
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            completedAt = Instant.now();
            return DeliveryOutcome.failed(true, null, "dispatch_interrupted", null, requestedAt, completedAt);
        } catch (Exception ex) {
            completedAt = Instant.now();
            boolean retryable = ex instanceof java.io.IOException;
            return DeliveryOutcome.failed(
                    retryable,
                    null,
                    trimToMax(trimToNull(ex.getMessage()), MAX_ERROR_LEN),
                    null,
                    requestedAt,
                    completedAt
            );
        }
    }

    private void assertUrlNotPrivateAddress(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("webhook_url_missing_host");
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    throw new IllegalArgumentException("webhook_url_resolves_to_private_address");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("webhook_url_unresolvable");
        }
    }

    private static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        // Blokovat IPv6-mapped IPv4 privátní adresy (např. ::ffff:127.0.0.1, ::ffff:10.0.0.1)
        if (addr instanceof Inet6Address inet6) {
            byte[] raw = inet6.getAddress();
            boolean isMapped = (raw[10] == (byte) 0xff && raw[11] == (byte) 0xff);
            if (isMapped) {
                try {
                    byte[] v4bytes = new byte[4];
                    System.arraycopy(raw, 12, v4bytes, 0, 4);
                    return isBlockedAddress(InetAddress.getByAddress(v4bytes));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildEnvelopeJson(WebhookDeliveryJob job, int attemptNo) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("id", job.getId().toString());
        envelope.put("type", job.getEventType());
        envelope.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : Instant.now().toString());
        envelope.put("attempt", attemptNo);
        envelope.put("tenantId", job.getTenantId().toString());
        if (job.getCorrelationId() != null) {
            envelope.put("correlationId", job.getCorrelationId().toString());
        }
        if (job.getRequestId() != null) {
            envelope.put("requestId", job.getRequestId());
        }
        envelope.set("data", job.getEventPayload() == null ? objectMapper.createObjectNode() : job.getEventPayload());
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("failed_to_serialize_webhook_payload", ex);
        }
    }

    private String sign(String secret, String payloadToSign) {
        String resolvedSecret = trimToNull(secret);
        if (resolvedSecret == null) {
            throw new IllegalStateException("webhook_secret_missing");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(resolvedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed_to_sign_webhook", ex);
        }
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private long computeBackoffMs(int attemptNo) {
        long base = Math.max(250L, baseDelayMs);
        long max = Math.max(base, maxDelayMs);
        long delay = base;
        for (int i = 1; i < attemptNo; i++) {
            if (delay >= max / 2) {
                delay = max;
                break;
            }
            delay *= 2;
        }
        delay = Math.min(delay, max);

        long jitterBound = Math.max(1L, delay / 5); // +/- 20%
        long jitter = ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        long withJitter = delay + jitter;
        return Math.max(base, Math.min(max, withJitter));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToMax(String value, int maxLen) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private record DeliveryOutcome(
            boolean success,
            boolean retryable,
            Integer statusCode,
            String errorMessage,
            String responseBody,
            Instant requestedAt,
            Instant completedAt
    ) {
        private static DeliveryOutcome success(Integer statusCode,
                                               String responseBody,
                                               Instant requestedAt,
                                               Instant completedAt) {
            return new DeliveryOutcome(true, false, statusCode, null, responseBody, requestedAt, completedAt);
        }

        private static DeliveryOutcome failed(boolean retryable,
                                              Integer statusCode,
                                              String errorMessage,
                                              String responseBody,
                                              Instant requestedAt,
                                              Instant completedAt) {
            return new DeliveryOutcome(false, retryable, statusCode, errorMessage, responseBody, requestedAt, completedAt);
        }
    }
}
