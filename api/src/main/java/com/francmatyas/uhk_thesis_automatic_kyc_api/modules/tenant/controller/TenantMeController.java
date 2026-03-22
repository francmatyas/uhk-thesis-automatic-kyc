package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto.TenantMemberUpsertRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenants/me")
@RequireActiveTenant
@RequiredArgsConstructor
public class TenantMeController {

    private final TenantService tenantService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.tenants:read')")
    public ResponseEntity<?> getCurrentTenant(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(tenantService.getCurrentTenant(currentUser));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.tenants:update')")
    public ResponseEntity<?> updateCurrentTenant(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateTenantRequest req,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (req == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_request"));
        }
        try {
            UUID tenantId = TenantContext.getTenantId();
            var updated = tenantService.updateCurrentTenant(currentUser, req.name, req.slug, req.members);
            audit(currentUser, httpReq, "TENANT", tenantId, "TENANT_UPDATE", null, updated);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
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

    @Data
    public static class UpdateTenantRequest {
        @Size(max = 100)
        public String name;
        @Size(max = 100)
        public String slug;
        @Valid
        public List<TenantMemberUpsertRequest> members;
    }
}
