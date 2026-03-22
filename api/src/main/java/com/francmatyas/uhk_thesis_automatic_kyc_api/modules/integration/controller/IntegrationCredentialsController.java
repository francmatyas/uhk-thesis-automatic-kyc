package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.CreateApiKeyRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.CreateWebhookEndpointRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.UpdateApiKeyRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto.UpdateWebhookEndpointRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.IntegrationCredentialsService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationCredentialsController {

    private final IntegrationCredentialsService integrationCredentialsService;
    private final AuditLogService auditLogService;

    @GetMapping("/api-keys")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.api-keys:read')")
    public ResponseEntity<?> getApiKeysTable(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String q
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(integrationCredentialsService.getApiKeysTable(currentUser, page, size, sort, dir, q));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api-keys/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.api-keys:read')")
    public ResponseEntity<?> getApiKeyDetail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID apiKeyId;
        try {
            apiKeyId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_api_key_id"));
        }

        try {
            return ResponseEntity.ok(integrationCredentialsService.getApiKeyDetail(currentUser, apiKeyId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api-keys")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.api-keys:create')")
    public ResponseEntity<?> createApiKey(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateApiKeyRequest request,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            var created = integrationCredentialsService.createApiKey(currentUser, request.getName());
            audit(currentUser, httpReq, "API_KEY", created.id(), "API_KEY_CREATE", null, Map.of("id", created.id(), "name", created.name()));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/api-keys/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.api-keys:update')")
    public ResponseEntity<?> updateApiKey(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody UpdateApiKeyRequest request,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID apiKeyId;
        try {
            apiKeyId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_api_key_id"));
        }

        try {
            var updated = integrationCredentialsService.updateApiKey(currentUser, apiKeyId, request.getName(), request.getStatus());
            audit(currentUser, httpReq, "API_KEY", id, "API_KEY_UPDATE", Map.of("id", id), updated);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.api-keys:delete')")
    public ResponseEntity<?> deleteApiKey(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID apiKeyId;
        try {
            apiKeyId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_api_key_id"));
        }

        try {
            integrationCredentialsService.deleteApiKey(currentUser, apiKeyId);
            audit(currentUser, httpReq, "API_KEY", id, "API_KEY_DELETE", Map.of("id", id), null);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/webhooks")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:read')")
    public ResponseEntity<?> getWebhooksTable(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String q
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(integrationCredentialsService.getWebhookEndpointsTable(currentUser, page, size, sort, dir, q));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/webhooks/options")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:create')")
    public ResponseEntity<?> getWebhookOptions(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(integrationCredentialsService.getWebhookEndpointOptions(currentUser));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/webhooks/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:read')")
    public ResponseEntity<?> getWebhookDetail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID webhookId;
        try {
            webhookId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_webhook_id"));
        }

        try {
            return ResponseEntity.ok(integrationCredentialsService.getWebhookEndpointDetail(currentUser, webhookId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:create')")
    public ResponseEntity<?> createWebhook(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateWebhookEndpointRequest request,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            var created = integrationCredentialsService.createWebhookEndpoint(
                    currentUser,
                    request.getUrl(),
                    request.getSecret(),
                    request.getEventTypes()
            );
            audit(currentUser, httpReq, "WEBHOOK_ENDPOINT", created.id(), "WEBHOOK_CREATE", null, Map.of("id", created.id(), "url", created.url()));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/webhooks/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:update')")
    public ResponseEntity<?> updateWebhook(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody UpdateWebhookEndpointRequest request,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID webhookId;
        try {
            webhookId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_webhook_id"));
        }

        try {
            var updated = integrationCredentialsService.updateWebhookEndpoint(currentUser, webhookId, request.getStatus(), request.getEventTypes());
            audit(currentUser, httpReq, "WEBHOOK_ENDPOINT", id, "WEBHOOK_UPDATE", Map.of("id", id), updated);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.webhooks:delete')")
    public ResponseEntity<?> deleteWebhook(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID webhookId;
        try {
            webhookId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_webhook_id"));
        }

        try {
            integrationCredentialsService.deleteWebhookEndpoint(currentUser, webhookId);
            audit(currentUser, httpReq, "WEBHOOK_ENDPOINT", id, "WEBHOOK_DELETE", Map.of("id", id), null);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    private void audit(User actor, HttpServletRequest req, String entityType, Object entityId, String action, Object oldValue, Object newValue) {
        try {
            String ip = req.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            auditLogService.logUserAction(
                    actor != null ? actor.getId() : null, TenantContext.getTenantId(),
                    entityType, entityId != null ? entityId.toString() : "unknown",
                    action, oldValue, newValue, null,
                    ip, req.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
    }
}
