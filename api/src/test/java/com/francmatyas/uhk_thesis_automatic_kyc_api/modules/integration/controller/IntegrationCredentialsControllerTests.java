package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.CreateApiKeyRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.CreateWebhookEndpointRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.UpdateApiKeyRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.UpdateWebhookEndpointRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.IntegrationCredentialsService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationCredentialsControllerTests {

    private static IntegrationCredentialsController controller(IntegrationCredentialsService service) {
        return new IntegrationCredentialsController(service, mock(AuditLogService.class));
    }

    private static MockHttpServletRequest req() {
        return new MockHttpServletRequest();
    }

    @Test
    void createApiKeyReturnsCreatedResponse() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("CI key");

        var response = new IntegrationCredentialsService.CreatedApiKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CI key",
                "pk_abc",
                "sk_abc",
                ApiKeyStatus.ACTIVE,
                Instant.now()
        );
        when(service.createApiKey(user, "CI key")).thenReturn(response);

        var res = controller.createApiKey(user, request, req());
        assertEquals(201, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    @Test
    void createWebhookReturnsCreatedResponse() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest();
        request.setUrl("https://example.com/hook");
        request.setSecret(null);

        var response = new IntegrationCredentialsService.CreatedWebhookEndpoint(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://example.com/hook",
                "whsec_abc",
                WebhookEndpointStatus.ACTIVE,
                Instant.now(),
                List.of("document.ready")
        );
        when(service.createWebhookEndpoint(user, "https://example.com/hook", null, null)).thenReturn(response);

        var res = controller.createWebhook(user, request, req());
        assertEquals(201, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    @Test
    void getApiKeysTableReturnsOkResponse() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        when(service.getApiKeysTable(user, 0, 10, "createdAt", "desc", null))
                .thenReturn(TableDTO.builder().rows(java.util.List.of()).columns(java.util.List.of()).build());

        var res = controller.getApiKeysTable(user, 0, 10, "createdAt", "desc", null);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void getWebhookOptionsReturnsOkResponse() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());

        when(service.getWebhookEndpointOptions(user))
                .thenReturn(new IntegrationCredentialsService.WebhookEndpointOptions(
                        List.of("ACTIVE", "DISABLED"),
                        List.of("document.ready", "document.deleted")
                ));

        var res = controller.getWebhookOptions(user);
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    @Test
    void getWebhookDetailWithInvalidIdReturnsBadRequest() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        var res = controller.getWebhookDetail(user, "invalid-id");

        assertEquals(400, res.getStatusCode().value());
        assertEquals(Map.of("error", "invalid_webhook_id"), res.getBody());
    }

    @Test
    void updateApiKeyReturnsOkResponse() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        UUID apiKeyId = UUID.randomUUID();
        UpdateApiKeyRequest request = new UpdateApiKeyRequest();
        request.setName("Renamed key");
        request.setStatus("REVOKED");

        when(service.updateApiKey(user, apiKeyId, "Renamed key", "REVOKED"))
                .thenReturn(Map.of("id", apiKeyId, "name", "Renamed key", "status", ApiKeyStatus.REVOKED));

        var res = controller.updateApiKey(user, apiKeyId.toString(), request, req());
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    @Test
    void deleteApiKeyReturnsNoContent() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        UUID apiKeyId = UUID.randomUUID();
        doNothing().when(service).deleteApiKey(user, apiKeyId);

        var res = controller.deleteApiKey(user, apiKeyId.toString(), req());
        assertEquals(204, res.getStatusCode().value());
    }

    @Test
    void updateWebhookPassesEventTypes() {
        IntegrationCredentialsService service = mock(IntegrationCredentialsService.class);
        IntegrationCredentialsController controller = controller(service);

        User user = new User();
        user.setId(UUID.randomUUID());
        UUID webhookId = UUID.randomUUID();
        UpdateWebhookEndpointRequest request = new UpdateWebhookEndpointRequest();
        request.setStatus("ACTIVE");
        request.setEventTypes(List.of("document.ready"));

        when(service.updateWebhookEndpoint(user, webhookId, "ACTIVE", List.of("document.ready")))
                .thenReturn(Map.of("id", webhookId));

        var res = controller.updateWebhook(user, webhookId.toString(), request, req());
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }
}
