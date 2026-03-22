package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceTests {

    @Test
    void createRevokesByFingerprintForWholeDeviceAcrossScopes() {
        UserSessionRepository repo = mock(UserSessionRepository.class);
        UserSessionService service = new UserSessionService(repo);

        User user = new User();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        user.setId(userId);

        when(repo.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                user,
                "jti-1",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "127.0.0.1",
                "test-agent",
                false,
                tenantId,
                Map.of()
        );

        verify(repo).revokeActiveForUserByFingerprint(
                eq(userId),
                eq("127.0.0.1"),
                eq("test-agent"),
                any(Instant.class),
                eq("SESSION_ROTATED")
        );
    }

    @Test
    void createRevokesByFingerprintWithinProviderScopeWhenTenantIsNull() {
        UserSessionRepository repo = mock(UserSessionRepository.class);
        UserSessionService service = new UserSessionService(repo);

        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        when(repo.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                user,
                "jti-2",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "127.0.0.1",
                "test-agent",
                false,
                null,
                Map.of()
        );

        verify(repo).revokeActiveForUserByFingerprint(
                eq(userId),
                eq("127.0.0.1"),
                eq("test-agent"),
                any(Instant.class),
                eq("SESSION_ROTATED")
        );
    }

    @Test
    void rotateOrCreateForSwitchUpdatesExistingSessionByPreviousJti() {
        UserSessionRepository repo = mock(UserSessionRepository.class);
        UserSessionService service = new UserSessionService(repo);

        User user = new User();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        user.setId(userId);

        UserSession existing = new UserSession();
        existing.setId(UUID.randomUUID());
        existing.setJti("old-jti");
        when(repo.findByJtiAndUserId("old-jti", userId)).thenReturn(java.util.Optional.of(existing));
        when(repo.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSession out = service.rotateOrCreateForSwitch(
                user,
                "old-jti",
                "new-jti",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "127.0.0.1",
                "test-agent",
                false,
                tenantId,
                Map.of()
        );

        verify(repo).findByJtiAndUserId("old-jti", userId);
        verify(repo).save(existing);
        org.junit.jupiter.api.Assertions.assertEquals("new-jti", out.getJti());
        org.junit.jupiter.api.Assertions.assertEquals(tenantId, out.getTenantId());
    }

    @Test
    void rotateOrCreateForSwitchFallsBackToFingerprintWhenPreviousJtiMissing() {
        UserSessionRepository repo = mock(UserSessionRepository.class);
        UserSessionService service = new UserSessionService(repo);

        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        UserSession existing = new UserSession();
        existing.setId(UUID.randomUUID());
        existing.setJti("active-jti");

        when(repo.findByJtiAndUserId("missing-jti", userId)).thenReturn(java.util.Optional.empty());
        when(repo.findActiveForUserByFingerprint(eq(userId), eq("127.0.0.1"), eq("test-agent"), any(Instant.class)))
                .thenReturn(List.of(existing));
        when(repo.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSession out = service.rotateOrCreateForSwitch(
                user,
                "missing-jti",
                "new-jti",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "127.0.0.1",
                "test-agent",
                false,
                null,
                Map.of()
        );

        verify(repo).findActiveForUserByFingerprint(eq(userId), eq("127.0.0.1"), eq("test-agent"), any(Instant.class));
        verify(repo).save(existing);
        org.junit.jupiter.api.Assertions.assertEquals("new-jti", out.getJti());
    }
}
