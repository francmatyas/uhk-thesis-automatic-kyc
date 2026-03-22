package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.exception.UserNotFoundException;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequireActiveTenant
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.members:read')")
    public ResponseEntity<?> fetchAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(required = false) String q
    ) {
        UUID tenantId = TenantContext.getTenantId();
        TableDTO users = userService.getAllTenantUsers(tenantId, page, size, sort, dir, q);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.members:read')")
    public ResponseEntity<?> fetchUserById(@PathVariable String id) {
        UUID tenantId = TenantContext.getTenantId();
        try {
            UserDTO user = userService.getTenantUserById(tenantId, id);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_user_id"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user_not_found"));
        }
    }

    /*@PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.members:create')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> userData) {
        try {
            UserDTO newUser = userService.createUser(userData);
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }*/

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.members:update')")
    public ResponseEntity<?> updateUser(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody UserDTO userDTO,
            HttpServletRequest httpReq
    ) {
        UUID tenantId = TenantContext.getTenantId();
        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_request"));
        }
        if (userDTO.getId() != null && !id.equals(userDTO.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "user_id_mismatch"));
        }
        userDTO.setId(id);

        try {
            UserDTO updatedUser = userService.updateTenantUser(tenantId, userDTO);
            audit(currentUser, httpReq, tenantId, "USER", id, "USER_UPDATE", null, updatedUser);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_user_id"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user_not_found"));
        }
    }

    private void audit(User actor, HttpServletRequest req, UUID tenantId, String entityType, Object entityId, String action, Object oldValue, Object newValue) {
        try {
            String ip = req.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            auditLogService.logUserAction(
                    actor != null ? actor.getId() : null, tenantId,
                    entityType, entityId != null ? entityId.toString() : "unknown",
                    action, oldValue, newValue, null,
                    ip, req.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
    }

}
