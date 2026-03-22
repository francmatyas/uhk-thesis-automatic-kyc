package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.exception.UserNotFoundException;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserPreferencesService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.PolicyService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserMeController {
    private final PolicyService policyService;
    private final UserPreferencesService userPreferencesService;
    private final UserSessionService sessions;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<?> me(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var snap = policyService.buildForUser(currentUser.getId());

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", currentUser.getId());
        userMap.put("email", currentUser.getEmail());
        userMap.put("fullName", currentUser.getFullName());
        userMap.put("givenName", currentUser.getGivenName());
        userMap.put("middleName", currentUser.getMiddleName());
        userMap.put("familyName", currentUser.getFamilyName());
        userMap.put("avatarUrl", currentUser.getProfile() != null ? currentUser.getProfile().getAvatarUrl() : null);
        userMap.put("isProviderUser", currentUser.isProviderUser());

        var sessionList = sessions.listActiveSessions(currentUser.getId()).stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("jti", s.getJti());
            m.put("issuedAt", s.getIssuedAt());
            m.put("expiresAt", s.getExpiresAt());
            m.put("lastSeenAt", s.getLastSeenAt());
            m.put("ipAddress", s.getIpAddress());
            m.put("deviceType", s.getDeviceType());
            m.put("deviceVendor", s.getDeviceVendor());
            m.put("deviceModel", s.getDeviceModel());
            m.put("osName", s.getOsName());
            m.put("osVersion", s.getOsVersion());
            m.put("browserName", s.getBrowserName());
            m.put("browserVersion", s.getBrowserVersion());
            m.put("cpuArch", s.getCpuArch());
            return m;
        }).collect(Collectors.toList());

        var tenants = membershipRepo.findAllByUserId(currentUser.getId()).stream().map(m -> {
            Map<String, Object> t = new HashMap<>();
            t.put("id", m.getTenant().getId());
            t.put("name", m.getTenant().getName());
            t.put("slug", m.getTenant().getSlug());
            t.put("status", m.getTenant().getStatus());
            t.put("isDefault", m.isDefault());
            return t;
        }).collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("user", userMap);
        body.put("activeTenantId", TenantContext.getTenantId());
        body.put("tenants", tenants);
        body.put("roles", snap.roles());
        body.put("permissions", snap.permissions());
        body.put("policyVersion", snap.policyVersion());
        body.put("sessions", sessionList);
        body.put("preferences", userPreferencesService.getUserPreferences(currentUser.getId()).getUserPreferences());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(userService.getUserById(currentUser.getId().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_user_id"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user_not_found"));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UserDTO userDTO,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_request"));
        }
        String currentUserId = currentUser.getId().toString();
        if (userDTO.getId() != null && !currentUserId.equals(userDTO.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "user_id_mismatch"));
        }
        userDTO.setId(currentUserId);

        try {
            var updated = userService.updateUser(userDTO);
            audit(currentUser, httpReq, "USER", currentUserId, "USER_UPDATE", null, updated);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_user_id"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user_not_found"));
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
