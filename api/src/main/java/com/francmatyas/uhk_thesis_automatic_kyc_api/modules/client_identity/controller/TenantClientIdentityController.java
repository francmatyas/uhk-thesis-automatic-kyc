package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.dto.ClientIdentityResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.dto.UpdateClientIdentityRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Čtení a úprava klientských identit, které se automaticky vytvořily
 * při vytvoření odpovídající verifikace.
 */
@RestController
@RequestMapping("/client-identities")
@RequireActiveTenant
@RequiredArgsConstructor
public class TenantClientIdentityController {

    private final ClientIdentityService service;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:read')")
    public ResponseEntity<?> list(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ClientIdentityResponse> result = service.findAllByTenant(TenantContext.getTenantId())
                .stream().map(ClientIdentityResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(ClientIdentityResponse.from(
                    service.findByIdAndTenant(id, TenantContext.getTenantId())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:update')")
    public ResponseEntity<?> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientIdentityRequest req,
            HttpServletRequest httpReq) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        try {
            ClientIdentity patch = new ClientIdentity();
            patch.setFirstName(req.getFirstName());
            patch.setLastName(req.getLastName());
            patch.setDateOfBirth(req.getDateOfBirth());
            patch.setCountryOfResidence(req.getCountryOfResidence());
            patch.setEmail(req.getEmail());
            patch.setDialCode(req.getDialCode());
            patch.setPhone(req.getPhone());
            patch.setExternalReference(req.getExternalReference());
            ClientIdentityResponse updated = ClientIdentityResponse.from(service.update(id, tenantId, patch));
            audit(currentUser, httpReq, tenantId, id, "CLIENT_IDENTITY_UPDATE", null, updated);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:delete')")
    public ResponseEntity<?> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        try {
            service.delete(id, tenantId);
            audit(currentUser, httpReq, tenantId, id, "CLIENT_IDENTITY_DELETE", null, null);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private void audit(User actor, HttpServletRequest req, UUID tenantId,
                       UUID entityId, String action, Object oldValue, Object newValue) {
        try {
            String ip = req.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            auditLogService.logUserAction(actor.getId(), tenantId, "CLIENT_IDENTITY",
                    entityId != null ? entityId.toString() : null,
                    action, oldValue, newValue, null, ip, req.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
    }
}
