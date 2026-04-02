package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.PermissionDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/provider/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_provider.permissions:read')")
    public ResponseEntity<TableDTO> fetchAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(permissionService.getAllPermissions(page, size, sort, dir, q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_provider.permissions:read')")
    public ResponseEntity<?> fetchById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(permissionService.getPermissionById(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /*@PostMapping
    @PreAuthorize("hasAuthority('PERM_provider.permissions:create')")
    public ResponseEntity<?> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody PermissionDTO dto, HttpServletRequest httpReq) {
        try {
            PermissionDTO created = permissionService.createPermission(dto);
            audit(currentUser, httpReq, "PERMISSION", created.getId(), "PERMISSION_CREATE", null, created);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_provider.permissions:update')")
    public ResponseEntity<?> update(@AuthenticationPrincipal User currentUser, @PathVariable String id, @Valid @RequestBody PermissionDTO dto, HttpServletRequest httpReq) {
        try {
            PermissionDTO updated = permissionService.updatePermission(id, dto);
            audit(currentUser, httpReq, "PERMISSION", id, "PERMISSION_UPDATE", Map.of("id", id), updated);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_provider.permissions:delete')")
    public ResponseEntity<?> delete(@AuthenticationPrincipal User currentUser, @PathVariable String id, HttpServletRequest httpReq) {
        try {
            permissionService.deletePermission(id);
            audit(currentUser, httpReq, "PERMISSION", id, "PERMISSION_DELETE", Map.of("id", id), null);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }*/

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
}
