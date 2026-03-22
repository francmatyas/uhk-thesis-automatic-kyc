package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.settings.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/settings/security")
@RequiredArgsConstructor
public class SettingsSecurityController {
    private final UserSessionService sessions;

    @GetMapping
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return ResponseEntity.status(401).build();

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

        Map<String, Object> body = new HashMap<>();
        body.put("sessions", sessionList);

        return ResponseEntity.ok(body);
    }
}
