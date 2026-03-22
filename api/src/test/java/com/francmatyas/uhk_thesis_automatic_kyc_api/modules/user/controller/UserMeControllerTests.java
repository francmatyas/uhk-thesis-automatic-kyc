package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.GetUserPreferencesDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserPreferencesDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserProfile;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserPreferencesService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.PolicyService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMeControllerTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void meReturnsUnauthorizedWhenUserMissing() {
        PolicyService policyService = mock(PolicyService.class);
        UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        UserService userService = mock(UserService.class);
        UserMeController controller = new UserMeController(
                policyService, userPreferencesService, sessions, membershipRepo, userService, mock(AuditLogService.class)
        );

        var res = controller.me(null);

        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void meReturnsCurrentUserPayload() {
        PolicyService policyService = mock(PolicyService.class);
        UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        UserService userService = mock(UserService.class);
        UserMeController controller = new UserMeController(
                policyService, userPreferencesService, sessions, membershipRepo, userService, mock(AuditLogService.class)
        );

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User user = new User();
        user.setId(userId);
        user.setEmail("john@example.com");
        user.setGivenName("John");
        user.setFamilyName("Doe");
        user.setFullName("John Doe");
        user.setProviderUser(false);

        UserProfile profile = new UserProfile();
        profile.setAvatarUrl("https://cdn.example.com/avatar.png");
        user.setProfile(profile);

        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setJti("session-jti");
        session.setIssuedAt(Instant.parse("2026-01-01T10:00:00Z"));
        session.setExpiresAt(Instant.parse("2026-01-02T10:00:00Z"));
        session.setLastSeenAt(Instant.parse("2026-01-01T11:00:00Z"));
        session.setIpAddress("127.0.0.1");
        session.setDeviceType("desktop");

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme");
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);

        UserTenantMembership membership = new UserTenantMembership();
        membership.setTenant(tenant);
        membership.setDefault(true);

        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of("OWNER"), List.of("tenant.members:read"), 3)
        );
        when(sessions.listActiveSessions(userId)).thenReturn(List.of(session));
        when(membershipRepo.findAllByUserId(userId)).thenReturn(List.of(membership));
        when(userPreferencesService.getUserPreferences(userId)).thenReturn(
                GetUserPreferencesDTO.builder()
                        .userPreferences(UserPreferencesDTO.builder().language("en").build())
                        .build()
        );

        var res = controller.me(user);

        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals(tenantId, body.get("activeTenantId"));
        assertEquals(3, body.get("policyVersion"));
        assertEquals(List.of("OWNER"), body.get("roles"));
        assertEquals(List.of("tenant.members:read"), body.get("permissions"));

        Map<String, Object> userMap = (Map<String, Object>) body.get("user");
        assertEquals(userId, userMap.get("id"));
        assertEquals("john@example.com", userMap.get("email"));
        assertEquals("https://cdn.example.com/avatar.png", userMap.get("avatarUrl"));

        List<Map<String, Object>> sessionsBody = (List<Map<String, Object>>) body.get("sessions");
        assertEquals(1, sessionsBody.size());
        assertEquals(sessionId, sessionsBody.get(0).get("id"));

        List<Map<String, Object>> tenantsBody = (List<Map<String, Object>>) body.get("tenants");
        assertEquals(1, tenantsBody.size());
        assertEquals("Acme", tenantsBody.get(0).get("name"));
    }

    @Test
    void getMyProfileReturnsCurrentUserProfile() {
        PolicyService policyService = mock(PolicyService.class);
        UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        UserService userService = mock(UserService.class);
        UserMeController controller = new UserMeController(
                policyService, userPreferencesService, sessions, membershipRepo, userService, mock(AuditLogService.class)
        );

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        UserDTO dto = UserDTO.builder().id(userId.toString()).email("john@example.com").build();
        when(userService.getUserById(userId.toString())).thenReturn(dto);

        var res = controller.getMyProfile(user);

        assertEquals(200, res.getStatusCode().value());
        assertEquals(dto, res.getBody());
    }

    @Test
    void updateMyProfileRejectsUserIdMismatch() {
        PolicyService policyService = mock(PolicyService.class);
        UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        UserService userService = mock(UserService.class);
        UserMeController controller = new UserMeController(
                policyService, userPreferencesService, sessions, membershipRepo, userService, mock(AuditLogService.class)
        );

        User user = new User();
        user.setId(UUID.randomUUID());

        UserDTO req = UserDTO.builder().id(UUID.randomUUID().toString()).build();

        var res = controller.updateMyProfile(user, req, new MockHttpServletRequest());

        assertEquals(400, res.getStatusCode().value());
        assertEquals(Map.of("error", "user_id_mismatch"), res.getBody());
    }

    @Test
    void updateMyProfileUsesCurrentUserIdAndReturnsUpdatedUser() {
        PolicyService policyService = mock(PolicyService.class);
        UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        UserService userService = mock(UserService.class);
        UserMeController controller = new UserMeController(
                policyService, userPreferencesService, sessions, membershipRepo, userService, mock(AuditLogService.class)
        );

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        UserDTO req = UserDTO.builder().fullName("John Doe").build();
        UserDTO updated = UserDTO.builder().id(userId.toString()).fullName("John Doe").build();
        when(userService.updateUser(any(UserDTO.class))).thenReturn(updated);

        var res = controller.updateMyProfile(user, req, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
        assertEquals(updated, res.getBody());

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(userService).updateUser(captor.capture());
        assertEquals(userId.toString(), captor.getValue().getId());
    }
}
