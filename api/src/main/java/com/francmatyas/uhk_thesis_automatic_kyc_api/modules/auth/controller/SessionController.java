package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final UserSessionService sessions;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<UserSession> list = sessions.listActiveSessions(me.getId());
        // Mapování entit do bezpečných map podobných DTO
        List<Map<String, Object>> items = list.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("jti", s.getJti());
            m.put("issuedAt", s.getIssuedAt());
            m.put("expiresAt", s.getExpiresAt());
            m.put("lastSeenAt", s.getLastSeenAt());
            m.put("ipAddress", s.getIpAddress());
            m.put("userAgent", s.getUserAgent());
            m.put("deviceType", s.getDeviceType());
            m.put("deviceVendor", s.getDeviceVendor());
            m.put("deviceModel", s.getDeviceModel());
            m.put("osName", s.getOsName());
            m.put("osVersion", s.getOsVersion());
            m.put("browserName", s.getBrowserName());
            m.put("browserVersion", s.getBrowserVersion());
            m.put("cpuArch", s.getCpuArch());
            m.put("rememberMe", s.isRememberMe());
            return m;
        }).toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revokeOne(@AuthenticationPrincipal User me, @RequestBody RevokeRequest req, HttpServletRequest httpReq) {
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = sessions.revokeByJtiForUser(me.getId(), req.jti, "USER_REVOKED");
        try {
            String ip = httpReq.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
            auditLogService.logUserAction(
                    me.getId(), null,
                    "USER_SESSION", req.jti != null ? req.jti : "unknown",
                    "SESSION_REVOKE", null, null,
                    Map.of("revoked", success),
                    ip, httpReq.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("revoked", success));
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<?> revokeAll(@AuthenticationPrincipal User me, HttpServletRequest httpReq) {
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        int count = sessions.revokeAllForUser(me.getId(), "USER_REVOKED_ALL");
        try {
            String ip = httpReq.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
            auditLogService.logUserAction(
                    me.getId(), null,
                    "USER", me.getId().toString(),
                    "SESSION_REVOKE_ALL", null, null,
                    Map.of("revokedCount", count),
                    ip, httpReq.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("revokedCount", count));
    }

    @Data
    public static class RevokeRequest {
        public String jti;
    }
}
