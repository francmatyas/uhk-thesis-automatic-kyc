package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

//import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.GeoInfo;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionService {
    private final UserSessionRepository repo;
    //private final GeoLocationService geoLocationService;

    public List<UserSession> listActiveSessions(UUID userId) {
        return repo.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now());
    }

    @Transactional
    public UserSession create(User user, String jti, Instant iat, Instant exp, String ip, String userAgent, boolean rememberMe, UUID tenantId, Map<String, Object> device) {
        //GeoInfo geoInfo = geoLocationService.resolve(ip);
        Instant now = Instant.now();
        // Udržujeme jedno aktivní sezení na uživatele + otisk zařízení. Tím se zabrání duplicitám,
        // když klient přepne oblast, ale nepošle poslední rotovaný token.
        repo.revokeActiveForUserByFingerprint(user.getId(), ip, userAgent, now, "SESSION_ROTATED");

        UserSession s = new UserSession();
        s.setUser(user);
        s.setJti(jti);
        s.setIssuedAt(iat);
        s.setExpiresAt(exp);
        s.setLastSeenAt(iat);
        s.setIpAddress(ip);
        s.setUserAgent(userAgent);
        s.setRememberMe(rememberMe);
        s.setTenantId(tenantId);
        applyDeviceInfo(s, device);

        return repo.save(s);
    }

    @Transactional
    public UserSession rotateOrCreateForSwitch(User user, String previousJti, String newJti, Instant iat, Instant exp, String ip, String userAgent, boolean rememberMe, UUID tenantId, Map<String, Object> device) {
        UserSession session = null;
        if (previousJti != null && !previousJti.isBlank()) {
            session = repo.findByJtiAndUserId(previousJti, user.getId()).orElse(null);
        }

        if (session == null) {
            session = repo.findActiveForUserByFingerprint(user.getId(), ip, userAgent, Instant.now())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        if (session == null) {
            return create(user, newJti, iat, exp, ip, userAgent, rememberMe, tenantId, device);
        }

        session.setJti(newJti);
        session.setIssuedAt(iat);
        session.setExpiresAt(exp);
        session.setLastSeenAt(iat);
        session.setIpAddress(ip);
        session.setUserAgent(userAgent);
        session.setRememberMe(rememberMe);
        session.setTenantId(tenantId);
        session.setRevoked(false);
        session.setRevokedAt(null);
        session.setRevokedReason(null);
        applyDeviceInfo(session, device);

        return repo.save(session);
    }

    public boolean isActive(String jti) {
        Optional<UserSession> s = repo.findByJtiAndRevokedFalse(jti);
        return s.isPresent() && (s.get().getExpiresAt() == null || s.get().getExpiresAt().isAfter(Instant.now()));
    }

    public Optional<UserSession> findByJti(String jti) {
        return repo.findByJti(jti);
    }

    @Transactional
    public void touch(String jti) {
        repo.touch(jti, Instant.now());
    }

    @Transactional
    public boolean revokeByJti(String jti, String reason) {
        return repo.findByJti(jti)
                .map(s -> repo.revokeByJtiForUser(s.getUser().getId(), jti, Instant.now(), reason) > 0)
                .orElse(false);
    }

    @Transactional
    public boolean revokeByJtiForUser(UUID userId, String jti, String reason) {
        return repo.revokeByJtiForUser(userId, jti, Instant.now(), reason) > 0;
    }

    @Transactional
    public int revokeAllForUser(UUID userId, String reason) {
        return repo.revokeAllActiveForUser(userId, Instant.now(), reason);
    }

    private void applyDeviceInfo(UserSession session, Map<String, Object> device) {
        if (device == null) {
            return;
        }
        session.setDeviceType((String) device.getOrDefault("deviceType", null));
        session.setDeviceVendor((String) device.getOrDefault("deviceVendor", null));
        session.setDeviceModel((String) device.getOrDefault("deviceModel", null));
        session.setOsName((String) device.getOrDefault("osName", null));
        session.setOsVersion((String) device.getOrDefault("osVersion", null));
        session.setBrowserName((String) device.getOrDefault("browserName", null));
        session.setBrowserVersion((String) device.getOrDefault("browserVersion", null));
        session.setCpuArch((String) device.getOrDefault("cpuArch", null));
    }
}
