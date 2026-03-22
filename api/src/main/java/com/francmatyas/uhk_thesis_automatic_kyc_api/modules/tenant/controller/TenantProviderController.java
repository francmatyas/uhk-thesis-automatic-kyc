package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto.TenantMemberUpsertRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

@RestController
@RequestMapping("/provider/tenants")
@RequiredArgsConstructor
public class TenantProviderController {

    private final TenantService tenantService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_provider.tenants:read')")
    public ResponseEntity<?> listAllTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(tenantService.getAllTenants(page, size, sort, dir, q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_provider.tenants:read')")
    public ResponseEntity<?> getTenant(@PathVariable String id) {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_id"));
        }

        try {
            return ResponseEntity.ok(tenantService.getTenantDetail(tenantId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_provider.tenants:create')")
    public ResponseEntity<?> createTenant(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpsertTenantRequest req,
            HttpServletRequest httpReq
    ) {
        try {
            String name = req != null ? req.name : null;
            String slug = req != null ? req.slug : null;
            String status = req != null ? req.status : null;
            var body = tenantService.createTenant(name, slug, status);
            audit(currentUser, httpReq, "TENANT", body.get("id"), "TENANT_CREATE", null, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_provider.tenants:update')")
    public ResponseEntity<?> updateTenant(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody UpsertTenantRequest req,
            HttpServletRequest httpReq
    ) {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_id"));
        }

        try {
            String name = req != null ? req.name : null;
            String slug = req != null ? req.slug : null;
            String status = req != null ? req.status : null;
            List<TenantMemberUpsertRequest> members = req != null ? req.members : null;
            var updated = tenantService.updateTenant(tenantId, name, slug, status, members);
            audit(currentUser, httpReq, "TENANT", id, "TENANT_UPDATE", null, updated);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_provider.tenants:delete')")
    public ResponseEntity<?> deleteTenant(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            HttpServletRequest httpReq
    ) {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_id"));
        }

        try {
            tenantService.deleteTenant(tenantId);
            audit(currentUser, httpReq, "TENANT", id, "TENANT_DELETE", Map.of("id", id), null);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private void audit(User actor, HttpServletRequest req, String entityType, Object entityId, String action, Object oldValue, Object newValue) {
        try {
            String ip = req.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            auditLogService.logUserAction(
                    actor != null ? actor.getId() : null, null,
                    entityType, entityId != null ? entityId.toString() : "unknown",
                    action, oldValue, newValue, null,
                    ip, req.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
    }

    @Data
    public static class UpsertTenantRequest {
        @Size(max = 100)
        public String name;
        @Size(max = 100)
        public String slug;
        @Size(max = 50)
        public String status;
        @Valid
        public List<TenantMemberUpsertRequest> members;
    }
}
