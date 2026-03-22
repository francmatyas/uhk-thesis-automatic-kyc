package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKey;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpoint;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointSubscription;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.ApiKeyRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.WebhookEndpointSubscriptionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationCredentialsServiceTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createApiKeyHashesSecretAndReturnsRawSecret() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(apiKeyRepository.findByPublicKey(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed-secret");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(Instant.now());
            return key;
        });

        var created = service.createApiKey(user, "My Integration Key");

        assertNotNull(created.id());
        assertEquals(tenantId, created.tenantId());
        assertEquals("My Integration Key", created.name());
        assertTrue(created.publicKey().startsWith("pk_"));
        assertTrue(created.secret().startsWith("sk_"));

        ArgumentCaptor<ApiKey> keyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        org.mockito.Mockito.verify(apiKeyRepository).save(keyCaptor.capture());
        assertEquals("hashed-secret", keyCaptor.getValue().getSecretHash());
    }

    @Test
    void createWebhookGeneratesSecretWhenMissing() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> {
            WebhookEndpoint endpoint = invocation.getArgument(0);
            endpoint.setId(UUID.randomUUID());
            endpoint.setCreatedAt(Instant.now());
            return endpoint;
        });

        var created = service.createWebhookEndpoint(user, "https://example.com/webhook", null, null);
        assertTrue(created.secret().startsWith("whsec_"));
        assertEquals("https://example.com/webhook", created.url());
        assertEquals(List.of(WebhookEventType.DOCUMENT_READY.eventName(), WebhookEventType.DOCUMENT_DELETED.eventName()), created.eventTypes());
    }

    @Test
    void createWebhookRejectsInvalidUrl() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createWebhookEndpoint(user, "not-a-url", null, null)
        );
        assertEquals("invalid_webhook_url", ex.getMessage());
    }

    @Test
    void getApiKeyDetailReturnsTenantScopedRecord() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(apiKeyId);
        apiKey.setName("Key A");
        apiKey.setPublicKey("pk_abc");
        apiKey.setStatus(com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus.ACTIVE);
        apiKey.setCreatedAt(Instant.now());

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)).thenReturn(Optional.of(apiKey));

        var detail = service.getApiKeyDetail(user, apiKeyId);
        assertEquals("Key A", detail.get("name"));
        assertEquals("pk_abc", detail.get("publicKey"));
    }

    @Test
    void getWebhookTableBuildsRows() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl("https://example.com/webhook");
        endpoint.setStatus(com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus.ACTIVE);
        endpoint.setCreatedAt(Instant.now());

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByTenantId(tenantId)).thenReturn(List.of(endpoint));

        var table = service.getWebhookEndpointsTable(user, 0, 10, "createdAt", "desc", null);
        assertEquals(1L, table.getTotalElements());
        assertEquals(1, table.getRows().size());
    }

    @Test
    void getWebhookDetailReturnsEventTypesAndEventTypeOptions() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(webhookId);
        endpoint.setTenant(tenant);
        endpoint.setUrl("https://example.com/webhook");
        endpoint.setSecret("whsec_abcdefghijklmnopqrst");
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setCreatedAt(Instant.now());

        WebhookEndpointSubscription subscription = WebhookEndpointSubscription.builder()
                .endpoint(endpoint)
                .eventType(WebhookEventType.DOCUMENT_READY)
                .enabled(true)
                .build();

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByIdAndTenantId(webhookId, tenantId)).thenReturn(Optional.of(endpoint));
        when(webhookEndpointSubscriptionRepository.findByEndpointIdOrderByEventTypeAsc(webhookId))
                .thenReturn(List.of(subscription));

        var detail = service.getWebhookEndpointDetail(user, webhookId);
        assertEquals(List.of("document.ready"), detail.get("eventTypes"));
        assertEquals(List.of("document.ready", "document.deleted"), detail.get("eventTypeOptions"));
        assertTrue(!detail.containsKey("secretPreview"));
    }

    @Test
    void getWebhookOptionsReturnsStatusesAndEventTypes() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var options = service.getWebhookEndpointOptions(user);
        assertEquals(List.of("ACTIVE", "DISABLED"), options.statuses());
        assertEquals(List.of("document.ready", "document.deleted"), options.eventTypes());
    }

    @Test
    void updateApiKeyUpdatesAllowedFields() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(apiKeyId);
        apiKey.setTenant(tenant);
        apiKey.setName("Old name");
        apiKey.setPublicKey("pk_abc");
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setCreatedAt(Instant.now());

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateApiKey(user, apiKeyId, "New name", "REVOKED");

        assertEquals("New name", updated.get("name"));
        assertEquals(ApiKeyStatus.REVOKED, updated.get("status"));
    }

    @Test
    void updateWebhookUpdatesStatus() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(webhookId);
        endpoint.setTenant(tenant);
        endpoint.setUrl("https://example.com/webhook");
        endpoint.setSecret("whsec_abcdefghijklmnopqrst");
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setCreatedAt(Instant.now());

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByIdAndTenantId(webhookId, tenantId)).thenReturn(Optional.of(endpoint));
        when(webhookEndpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateWebhookEndpoint(user, webhookId, "DISABLED", null);

        assertEquals(WebhookEndpointStatus.DISABLED, updated.get("status"));
    }

    @Test
    void updateWebhookReplacesSubscriptionsFlushesDeleteBeforeInsert() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(webhookId);
        endpoint.setTenant(tenant);
        endpoint.setUrl("https://example.com/webhook");
        endpoint.setSecret("whsec_abcdefghijklmnopqrst");
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setCreatedAt(Instant.now());

        WebhookEndpointSubscription storedSubscription = WebhookEndpointSubscription.builder()
                .endpoint(endpoint)
                .eventType(WebhookEventType.DOCUMENT_READY)
                .enabled(true)
                .build();

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByIdAndTenantId(webhookId, tenantId)).thenReturn(Optional.of(endpoint));
        when(webhookEndpointSubscriptionRepository.findByEndpointIdOrderByEventTypeAsc(webhookId))
                .thenReturn(List.of(storedSubscription));

        service.updateWebhookEndpoint(user, webhookId, null, List.of("document.ready"));

        var order = inOrder(webhookEndpointSubscriptionRepository);
        order.verify(webhookEndpointSubscriptionRepository).deleteByEndpointId(webhookId);
        order.verify(webhookEndpointSubscriptionRepository).flush();
        order.verify(webhookEndpointSubscriptionRepository).saveAll(anyList());
    }

    @Test
    void deleteApiKeyDeletesTenantScopedRecord() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        WebhookEndpointRepository webhookEndpointRepository = mock(WebhookEndpointRepository.class);
        WebhookEndpointSubscriptionRepository webhookEndpointSubscriptionRepository = mock(WebhookEndpointSubscriptionRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        IntegrationCredentialsService service = new IntegrationCredentialsService(
                apiKeyRepository,
                webhookEndpointRepository,
                webhookEndpointSubscriptionRepository,
                tenantRepository,
                tenantAccessService,
                passwordEncoder
        );

        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        TenantContext.setTenantId(tenantId);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(apiKeyId);
        apiKey.setTenant(tenant);

        when(tenantAccessService.canAccessTenant(user, tenantId)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)).thenReturn(Optional.of(apiKey));

        service.deleteApiKey(user, apiKeyId);

        verify(apiKeyRepository).delete(apiKey);
    }
}
