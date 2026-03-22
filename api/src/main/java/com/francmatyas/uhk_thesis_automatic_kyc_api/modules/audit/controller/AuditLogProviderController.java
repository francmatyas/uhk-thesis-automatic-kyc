package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/provider/audit-logs")
@RequiredArgsConstructor
public class AuditLogProviderController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_provider.audit-logs:read')")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorUserId
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


        UUID tenantUuid = null;
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                tenantUuid = UUID.fromString(tenantId.trim());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_tenant_id"));
            }
        }

        UUID actorUuid = null;
        if (actorUserId != null && !actorUserId.isBlank()) {
            try {
                actorUuid = UUID.fromString(actorUserId.trim());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_actor_user_id"));
            }
        }

        return ResponseEntity.ok(auditLogService.getProviderAuditLogsTable(tenantUuid, page, size, entityType, action, actorUuid));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_provider.audit-logs:read')")
    public ResponseEntity<?> detail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_audit_log_id"));
        }

        try {
            return ResponseEntity.ok(auditLogService.getProviderAuditLogDetail(uuid));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

}
