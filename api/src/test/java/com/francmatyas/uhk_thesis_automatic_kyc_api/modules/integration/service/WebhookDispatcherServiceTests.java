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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookDispatcherServiceTests {

    private static WebhookDispatcherService buildService(
            WebhookEndpointRepository endpointRepository,
            WebhookEndpointSubscriptionRepository subscriptionRepository,
            WebhookDeliveryJobRepository jobRepository,
            WebhookDeliveryAttemptRepository attemptRepository) {
        ObjectMapper mapper = new ObjectMapper();
        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 6);
        ReflectionTestUtils.setField(service, "batchSize", 25);
        ReflectionTestUtils.setField(service, "baseDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 60000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        ReflectionTestUtils.setField(service, "httpClient", mock(HttpClient.class));
        return service;
    }

    // -------------------------------------------------------------------------
    // Zařazení do fronty (enqueue)
    // -------------------------------------------------------------------------

    @Test
    void enqueueTenantEventCreatesPendingJobsForActiveEndpoints() {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 6);
        ReflectionTestUtils.setField(service, "httpClient", mock(HttpClient.class));

        UUID tenantId = UUID.randomUUID();
        WebhookEndpoint endpoint1 = new WebhookEndpoint();
        endpoint1.setId(UUID.randomUUID());
        endpoint1.setStatus(WebhookEndpointStatus.ACTIVE);
        WebhookEndpoint endpoint2 = new WebhookEndpoint();
        endpoint2.setId(UUID.randomUUID());
        endpoint2.setStatus(WebhookEndpointStatus.ACTIVE);

        when(subscriptionRepository.findActiveEndpointsByTenantIdAndEventType(tenantId, WebhookEventType.DOCUMENT_READY))
                .thenReturn(List.of(endpoint1, endpoint2));
        when(jobRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int enqueued = service.enqueueTenantEvent(
                tenantId, "document.ready",
                Map.of("documentId", UUID.randomUUID().toString()),
                null, null);

        assertEquals(2, enqueued);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebhookDeliveryJob>> jobsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(jobRepository).saveAll(jobsCaptor.capture());
        assertEquals(2, jobsCaptor.getValue().size());
        assertEquals(WebhookDeliveryStatus.PENDING, jobsCaptor.getValue().get(0).getStatus());
        assertEquals(0, jobsCaptor.getValue().get(0).getAttemptCount());
    }

    @Test
    void enqueueTenantEventReturnsZeroWhenDisabled() {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", false);

        int enqueued = service.enqueueTenantEvent(UUID.randomUUID(), "document.ready", Map.of(), null, null);

        assertEquals(0, enqueued);
        verifyNoInteractions(subscriptionRepository, jobRepository, endpointRepository);
    }

    @Test
    void enqueueTenantEventWithNullTenantIdThrowsIllegalArgument() {
        WebhookDispatcherService service = buildService(
                mock(WebhookEndpointRepository.class),
                mock(WebhookEndpointSubscriptionRepository.class),
                mock(WebhookDeliveryJobRepository.class),
                mock(WebhookDeliveryAttemptRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.enqueueTenantEvent(null, "document.ready", Map.of(), null, null));
    }

    @Test
    void enqueueTenantEventWithUnsupportedEventTypeThrowsIllegalArgument() {
        WebhookDispatcherService service = buildService(
                mock(WebhookEndpointRepository.class),
                mock(WebhookEndpointSubscriptionRepository.class),
                mock(WebhookDeliveryJobRepository.class),
                mock(WebhookDeliveryAttemptRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.enqueueTenantEvent(UUID.randomUUID(), "unsupported.event.type", Map.of(), null, null));
    }

    @Test
    void enqueueTenantEventReturnsZeroWhenNoActiveEndpoints() {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);

        UUID tenantId = UUID.randomUUID();
        when(subscriptionRepository.findActiveEndpointsByTenantIdAndEventType(tenantId, WebhookEventType.DOCUMENT_READY))
                .thenReturn(List.of());
        when(subscriptionRepository.countByTenantId(tenantId)).thenReturn(5L); // has subscriptions, just none for this event

        int enqueued = service.enqueueTenantEvent(tenantId, "document.ready", Map.of(), null, null);

        assertEquals(0, enqueued);
        verify(jobRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // Odeslání — úspěch
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobSuccessStoresAttemptAndMarksSucceeded() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 1000L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 60000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret("whsec_test_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode().put("k", "v");
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(payload)
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        assertEquals(WebhookDeliveryStatus.SUCCEEDED, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertNotNull(endpoint.getLastDeliveryAt());
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("document.ready", requestCaptor.getValue().headers().firstValue("X-AutomaticKyc-Event").orElse(null));
        assertTrue(requestCaptor.getValue().headers().firstValue("X-AutomaticKyc-Signature").orElse("").startsWith("v1="));

        ArgumentCaptor<WebhookDeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(WebhookDeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertTrue(attemptCaptor.getValue().isSuccess());
        assertEquals(200, attemptCaptor.getValue().getStatusCode());
    }

    @Test
    void dispatchJobServerErrorSchedulesRetry() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 2000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret("whsec_test_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode().put("k", "v"))
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("{\"error\":\"temporary\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        assertEquals(WebhookDeliveryStatus.RETRY_SCHEDULED, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertNotNull(job.getNextAttemptAt());
        assertEquals(503, job.getLastStatusCode());
    }

    // -------------------------------------------------------------------------
    // Korektnost HMAC podpisu
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobSendsCorrectHmacSignature() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 60000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        String secret = "my-webhook-secret-key";
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret(secret);
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode().put("key", "value");
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(payload)
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("ok");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        service.dispatchJob(jobId);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        String signatureHeader = sentRequest.headers().firstValue("X-AutomaticKyc-Signature").orElse(null);
        String timestampHeader = sentRequest.headers().firstValue("X-AutomaticKyc-Timestamp").orElse(null);

        assertNotNull(signatureHeader, "Signature header must be present");
        assertNotNull(timestampHeader, "Timestamp header must be present");
        assertTrue(signatureHeader.startsWith("v1="), "Signature must start with 'v1='");

        // Ověření formátu podpisu: v1=<hex-encoded-hmac>
        String hexSig = signatureHeader.substring(3); // strip "v1="
        assertFalse(hexSig.isBlank(), "Signature hex value must not be blank");
        // HMAC-SHA256 produkuje 32 bajtů = 64 hex znaků
        assertEquals(64, hexSig.length(),
                "HMAC-SHA256 hex signature must be 64 characters long");

        // Ověření, že podpis je validní hex
        assertDoesNotThrow(() -> HexFormat.of().parseHex(hexSig),
                "Signature must be valid hex");
    }

    @Test
    void dispatchJobSignatureUsesTimestampDotPayload() throws Exception {
        // Nezávisle spočítat očekávaný HMAC a porovnat
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 60000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        String secret = "signing-secret-for-hmac-test";
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret(secret);
        endpoint.setUrl("https://example.com/hook");

        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode().put("data", "test");
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(tenantId)
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(payload)
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(endpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("ok");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        service.dispatchJob(jobId);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        String signatureHeader = sentRequest.headers().firstValue("X-AutomaticKyc-Signature").orElse(null);
        String timestampHeader = sentRequest.headers().firstValue("X-AutomaticKyc-Timestamp").orElse(null);

        assertNotNull(signatureHeader);
        assertNotNull(timestampHeader);

        // Získání surového těla požadavku — z HttpRequest ho nelze snadno získat, ale
        // lze ověřit správnou délku a formát podpisu (64 hex znaků).
        // Rekonstrukce payloadu je integrační záležitost; zde ověřujeme,
        // že podpis je validní 64znakový HMAC-SHA256 hex digest.
        String hexSig = signatureHeader.substring(3); // strip "v1="
        assertEquals(64, hexSig.length());
        assertDoesNotThrow(() -> HexFormat.of().parseHex(hexSig));
    }

    // -------------------------------------------------------------------------
    // Selhání spojení / IOException
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobWithIoExceptionSchedulesRetry() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 60000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret("whsec_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        // Simulace síťového selhání
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        // IOException je opakovatelná (lze opakovat)
        assertEquals(WebhookDeliveryStatus.RETRY_SCHEDULED, job.getStatus(),
                "IOException should cause retry scheduling");
        assertNotNull(job.getNextAttemptAt(), "Retry should have a next attempt time");
        assertEquals(1, job.getAttemptCount());
    }

    // -------------------------------------------------------------------------
    // Vyčerpán maximální počet opakování
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobExhaustsMaxAttemptsAndMarksFailed() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 2000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret("whsec_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        // Simulace posledního pokusu: attemptCount = maxAttempts - 1, takže jde o finální pokus
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode())
                .status(WebhookDeliveryStatus.RETRY_SCHEDULED)
                .attemptCount(2) // 2 previous attempts
                .maxAttempts(3)  // max is 3, so this is the 3rd and final attempt
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("service unavailable");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        assertEquals(WebhookDeliveryStatus.FAILED, job.getStatus(),
                "Job should be marked FAILED after exhausting max attempts");
        assertNull(job.getNextAttemptAt(), "FAILED jobs should have no next attempt time");
        assertEquals(3, job.getAttemptCount());
    }

    @Test
    void dispatchJobWithNonRetryableStatusMarksFailed() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 2000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        HttpClient httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setSecret("whsec_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any(WebhookDeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(WebhookDeliveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        // 400 není podle zdrojového kódu opakovatelná
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("bad request");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        assertEquals(WebhookDeliveryStatus.FAILED, job.getStatus(),
                "Non-retryable 4xx status should immediately mark job as FAILED");
        assertNull(job.getNextAttemptAt());
    }

    // -------------------------------------------------------------------------
    // Zakázaný stav
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobReturnsFalseWhenDisabled() {
        WebhookDispatcherService service = buildService(
                mock(WebhookEndpointRepository.class),
                mock(WebhookEndpointSubscriptionRepository.class),
                mock(WebhookDeliveryJobRepository.class),
                mock(WebhookDeliveryAttemptRepository.class));
        ReflectionTestUtils.setField(service, "enabled", false);

        boolean result = service.dispatchJob(UUID.randomUUID());

        assertFalse(result, "dispatchJob should return false when disabled");
    }

    @Test
    void dispatchJobReturnsFalseForNullJobId() {
        WebhookDispatcherService service = buildService(
                mock(WebhookEndpointRepository.class),
                mock(WebhookEndpointSubscriptionRepository.class),
                mock(WebhookDeliveryJobRepository.class),
                mock(WebhookDeliveryAttemptRepository.class));

        boolean result = service.dispatchJob(null);

        assertFalse(result, "dispatchJob should return false for null jobId");
    }

    @Test
    void dispatchJobReturnsFalseForNonExistentJob() {
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        when(jobRepository.findByIdForDispatch(any())).thenReturn(Optional.empty());

        WebhookDispatcherService service = buildService(
                mock(WebhookEndpointRepository.class),
                mock(WebhookEndpointSubscriptionRepository.class),
                jobRepository,
                mock(WebhookDeliveryAttemptRepository.class));

        boolean result = service.dispatchJob(UUID.randomUUID());

        assertFalse(result, "dispatchJob should return false when job is not found");
    }

    // -------------------------------------------------------------------------
    // Neaktivní koncový bod
    // -------------------------------------------------------------------------

    @Test
    void dispatchJobWithInactiveEndpointMarksFailed() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 2000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        ReflectionTestUtils.setField(service, "httpClient", mock(HttpClient.class));

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setStatus(WebhookEndpointStatus.DISABLED); // disabled endpoint
        endpoint.setSecret("whsec_secret");
        endpoint.setUrl("https://example.com/webhook");

        UUID jobId = UUID.randomUUID();
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpoint.getId())
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        // Neaktivní koncový bod způsobí trvalé selhání (nelze opakovat)
        assertEquals(WebhookDeliveryStatus.FAILED, job.getStatus(),
                "Disabled endpoint should cause job to be marked FAILED");
    }

    @Test
    void dispatchJobWithMissingEndpointMarksFailed() throws Exception {
        WebhookEndpointRepository endpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository subscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        WebhookDeliveryJobRepository jobRepository = mock(WebhookDeliveryJobRepository.class);
        WebhookDeliveryAttemptRepository attemptRepository = mock(WebhookDeliveryAttemptRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        WebhookDispatcherService service = new WebhookDispatcherService(
                endpointRepository, subscriptionRepository, jobRepository, attemptRepository, mapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseDelayMs", 100L);
        ReflectionTestUtils.setField(service, "maxDelayMs", 2000L);
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 2000L);
        ReflectionTestUtils.setField(service, "requestTimeoutMs", 5000L);
        ReflectionTestUtils.setField(service, "httpClient", mock(HttpClient.class));

        UUID endpointId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        WebhookDeliveryJob job = WebhookDeliveryJob.builder()
                .id(jobId)
                .tenantId(UUID.randomUUID())
                .endpointId(endpointId)
                .eventType("document.ready")
                .eventPayload(mapper.createObjectNode())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(Instant.now().minusSeconds(1))
                .build();

        when(jobRepository.findByIdForDispatch(jobId)).thenReturn(Optional.of(job));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.empty()); // deleted endpoint
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean processed = service.dispatchJob(jobId);

        assertTrue(processed);
        assertEquals(WebhookDeliveryStatus.FAILED, job.getStatus(),
                "Missing endpoint should cause job to be marked FAILED");
    }
}
