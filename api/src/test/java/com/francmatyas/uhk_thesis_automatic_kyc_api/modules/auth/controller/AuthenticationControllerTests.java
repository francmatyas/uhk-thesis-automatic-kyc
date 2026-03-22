package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.AppProps;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.JwtService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.PolicyService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationControllerTests {

    // -------------------------------------------------------------------------
    // Pomocné factory metody
    // -------------------------------------------------------------------------

    private static AuthenticationController controller(
            AuthenticationManager authManager,
            UserEmailLookupService userEmailLookupService,
            UserRepository userRepo,
            JwtService jwtService,
            PolicyService policyService,
            AppProps props,
            UserSessionService sessionService,
            UserTenantMembershipRepository membershipRepo,
            TenantAccessService tenantAccessService) {
        return new AuthenticationController(
                authManager,
                mock(PasswordEncoder.class),
                userRepo,
                userEmailLookupService,
                jwtService,
                policyService,
                props,
                sessionService,
                membershipRepo,
                tenantAccessService,
                null,
                mock(AuditLogService.class)
        );
    }

    private static AppProps testProps(String cookieName) {
        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(cookieName);
        when(props.jwtSameSite()).thenReturn("Lax");
        when(props.isProd()).thenReturn(false);
        when(props.jwtAccessTtlMinutes()).thenReturn(60);
        when(props.jwtRememberTtlMinutes()).thenReturn(10080);
        return props;
    }

    private static UserSession stubSession(User user, String jti) {
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setJti(jti);
        session.setIssuedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        session.setLastSeenAt(Instant.now());
        return session;
    }

    // -------------------------------------------------------------------------
    // CSRF koncový bod
    // -------------------------------------------------------------------------

    @Test
    void csrfEndpointReturnsTokenMetadata() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        JwtService jwtService = mock(JwtService.class);
        PolicyService policyService = mock(PolicyService.class);
        AppProps props = testProps("AUTH_TOKEN");
        UserSessionService sessionService = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        var token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "abc123");
        var res = ctrl.csrf(token);

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals("abc123", body.get("token"));
        assertEquals("X-XSRF-TOKEN", body.get("headerName"));
        assertEquals("_csrf", body.get("parameterName"));
    }

    // -------------------------------------------------------------------------
    // Login — úspěšné scénáře
    // -------------------------------------------------------------------------

    @Test
    void successfulLoginWithoutTenantReturns200WithUserAndSessionInBody() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");
        user.setGivenName("Alice");
        user.setFamilyName("Smith");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of("OWNER"), List.of("provider.users:read"), 1));

        JwtService jwtService = mock(JwtService.class);
        String fakeJwt = "fake.jwt.token";
        when(jwtService.createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap()))
                .thenReturn(fakeJwt);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .build();
        when(jwtService.parseAndVerify(fakeJwt)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        UserSession session = stubSession(user, JwtService.getJti(claims));
        when(sessionService.create(eq(user), anyString(), any(), any(), any(), any(),
                eq(false), isNull(), any())).thenReturn(session);

        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        when(membershipRepo.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        AppProps props = testProps("AUTH_TOKEN");
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "alice@example.com";
        req.password = "secret";
        req.rememberMe = false;

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/login");
        var res = ctrl.login(req, httpReq);

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("user"), "Response body must contain 'user'");
        assertTrue(body.containsKey("roles"), "Response body must contain 'roles'");
        assertTrue(body.containsKey("permissions"), "Response body must contain 'permissions'");
        assertTrue(body.containsKey("session"), "Response body must contain 'session'");
        assertTrue(body.containsKey("tenants"), "Response body must contain 'tenants'");

        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) body.get("user");
        assertEquals(userId, userMap.get("id"));
        assertEquals("alice@example.com", userMap.get("email"));
    }

    @Test
    void successfulLoginResponseIncludesSetCookieHeader() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of(), List.of(), 1));

        String fakeJwt = "fake.jwt.token";
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap()))
                .thenReturn(fakeJwt);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .build();
        when(jwtService.parseAndVerify(fakeJwt)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.create(eq(user), anyString(), any(), any(), any(), any(),
                eq(false), isNull(), any())).thenReturn(stubSession(user, "jti"));

        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        when(membershipRepo.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        AppProps props = testProps("AUTH_TOKEN");
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "alice@example.com";
        req.password = "secret";
        req.rememberMe = false;

        var res = ctrl.login(req, new MockHttpServletRequest("POST", "/auth/login"));

        assertEquals(200, res.getStatusCode().value());
        String setCookie = res.getHeaders().getFirst("Set-Cookie");
        assertNotNull(setCookie, "Response must set a cookie");
        assertTrue(setCookie.contains("AUTH_TOKEN"), "Cookie must be named AUTH_TOKEN");
        assertTrue(setCookie.contains("HttpOnly"), "Auth cookie must be HttpOnly");
    }

    @Test
    void loginWithTenantsPopulatesTenantsListInBody() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("bob@example.com");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("bob@example.com")).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of("MEMBER"), List.of(), 1));

        String fakeJwt = "fake.jwt.token";
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap()))
                .thenReturn(fakeJwt);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .build();
        when(jwtService.parseAndVerify(fakeJwt)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.create(eq(user), anyString(), any(), any(), any(), any(),
                eq(false), isNull(), any())).thenReturn(stubSession(user, "jti"));

        // Vytvoření dvou tenant membership vazeb
        Tenant t1 = new Tenant();
        t1.setId(UUID.randomUUID());
        t1.setName("Acme Corp");
        t1.setSlug("acme");
        t1.setStatus(TenantStatus.ACTIVE);

        Tenant t2 = new Tenant();
        t2.setId(UUID.randomUUID());
        t2.setName("Beta Inc");
        t2.setSlug("beta");
        t2.setStatus(TenantStatus.ACTIVE);

        UserTenantMembership m1 = new UserTenantMembership();
        m1.setUser(user);
        m1.setTenant(t1);
        m1.setDefault(true);

        UserTenantMembership m2 = new UserTenantMembership();
        m2.setUser(user);
        m2.setTenant(t2);
        m2.setDefault(false);

        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        when(membershipRepo.findAllByUserId(userId)).thenReturn(List.of(m1, m2));

        AppProps props = testProps("AUTH_TOKEN");
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "bob@example.com";
        req.password = "secret";

        var res = ctrl.login(req, new MockHttpServletRequest("POST", "/auth/login"));

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tenants = (List<Map<String, Object>>) body.get("tenants");
        assertEquals(2, tenants.size());
    }

    // -------------------------------------------------------------------------
    // Login — chybové scénáře
    // -------------------------------------------------------------------------

    @Test
    void loginWithInvalidCredentialsReturns401() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        PolicyService policyService = mock(PolicyService.class);
        JwtService jwtService = mock(JwtService.class);
        AppProps props = testProps("AUTH_TOKEN");
        UserSessionService sessionService = mock(UserSessionService.class);
        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "bad@example.com";
        req.password = "wrongpassword";

        var res = ctrl.login(req, new MockHttpServletRequest("POST", "/auth/login"));

        assertEquals(401, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals("ERROR_INVALID_CREDENTIALS", body.get("error"));
    }

    @Test
    void loginWithInvalidCredentialsDoesNotIssueJwt() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        JwtService jwtService = mock(JwtService.class);
        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        AppProps props = testProps("AUTH_TOKEN");

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, mock(PolicyService.class), props,
                mock(UserSessionService.class), mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class));

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "bad@example.com";
        req.password = "wrongpassword";

        ctrl.login(req, new MockHttpServletRequest("POST", "/auth/login"));

        verify(jwtService, never()).createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap());
    }

    @Test
    void loginWithTenantHeaderWithoutAccessReturns403() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        when(tenantAccessService.canAccessTenant(any(User.class), any(UUID.class))).thenReturn(false);

        AppProps props = testProps("AUTH_TOKEN");

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), mock(JwtService.class), mock(PolicyService.class), props,
                mock(UserSessionService.class), mock(UserTenantMembershipRepository.class),
                tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "user@example.com";
        req.password = "secret";

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/login");
        httpReq.addHeader("X-Tenant-Id", UUID.randomUUID().toString());

        var res = ctrl.login(req, httpReq);

        assertEquals(403, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals("forbidden", body.get("error"));
        assertEquals("not_member_of_tenant", body.get("reason"));
    }

    @Test
    void loginWithInvalidTenantHeaderUuidIsIgnoredAndProceedsWithoutTenant() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of(), List.of(), 1));

        String fakeJwt = "fake.jwt.token";
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap()))
                .thenReturn(fakeJwt);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .build();
        when(jwtService.parseAndVerify(fakeJwt)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.create(eq(user), anyString(), any(), any(), any(), any(),
                eq(false), isNull(), any())).thenReturn(stubSession(user, "jti"));

        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        when(membershipRepo.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        // tenantAccessService se NESMÍ volat, protože hlavička není validní UUID
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        AppProps props = testProps("AUTH_TOKEN");

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, tenantAccessService);

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "alice@example.com";
        req.password = "secret";

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/login");
        httpReq.addHeader("X-Tenant-Id", "not-a-uuid");

        var res = ctrl.login(req, httpReq);

        assertEquals(200, res.getStatusCode().value());
        verify(tenantAccessService, never()).canAccessTenant(any(), any());
    }

    // -------------------------------------------------------------------------
    // Odhlášení
    // -------------------------------------------------------------------------

    @Test
    void logoutWithValidCookieRevokesSessions() {
        AppProps props = testProps("AUTH_TOKEN");

        String jti = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        JwtService jwtService = mock(JwtService.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .build();
        when(jwtService.parseAndVerify("valid.jwt.token")).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.revokeByJtiForUser(userId, jti, "USER_LOGGOUT")).thenReturn(true);

        AuthenticationController ctrl = controller(mock(AuthenticationManager.class),
                mock(UserEmailLookupService.class), mock(UserRepository.class),
                jwtService, mock(PolicyService.class), props, sessionService,
                mock(UserTenantMembershipRepository.class), mock(TenantAccessService.class));

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/logout");
        httpReq.setCookies(new Cookie("AUTH_TOKEN", "valid.jwt.token"));

        var res = ctrl.logout(httpReq);

        assertEquals(204, res.getStatusCode().value());
        verify(sessionService).revokeByJtiForUser(userId, jti, "USER_LOGGOUT");
    }

    @Test
    void logoutWithoutCookieReturns204WithClearCookie() {
        AppProps props = testProps("AUTH_TOKEN");

        AuthenticationController ctrl = controller(mock(AuthenticationManager.class),
                mock(UserEmailLookupService.class), mock(UserRepository.class),
                mock(JwtService.class), mock(PolicyService.class), props,
                mock(UserSessionService.class), mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class));

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/logout");

        var res = ctrl.logout(httpReq);

        assertEquals(204, res.getStatusCode().value());
        String setCookie = res.getHeaders().getFirst("Set-Cookie");
        assertNotNull(setCookie, "Logout must clear the cookie");
        assertTrue(setCookie.contains("AUTH_TOKEN"), "Cleared cookie must be AUTH_TOKEN");
        // Max-Age=0 vymaže cookie
        assertTrue(setCookie.contains("Max-Age=0") || setCookie.contains("max-age=0"),
                "Logout must expire the cookie immediately");
    }

    @Test
    void logoutWithInvalidTokenStillReturns204() {
        AppProps props = testProps("AUTH_TOKEN");

        JwtService jwtService = mock(JwtService.class);
        when(jwtService.parseAndVerify(anyString())).thenThrow(new RuntimeException("invalid token"));

        AuthenticationController ctrl = controller(mock(AuthenticationManager.class),
                mock(UserEmailLookupService.class), mock(UserRepository.class),
                jwtService, mock(PolicyService.class), props,
                mock(UserSessionService.class), mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class));

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/auth/logout");
        httpReq.setCookies(new Cookie("AUTH_TOKEN", "garbage-token"));

        var res = ctrl.logout(httpReq);

        // Nesmí vyhodit výjimku; řízená degradace
        assertEquals(204, res.getStatusCode().value());
    }

    // -------------------------------------------------------------------------
    // Registrace
    // -------------------------------------------------------------------------

    @Test
    void registerWithNewEmailReturns200Ok() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("newuser@example.com")).thenReturn(Optional.empty());

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthenticationController ctrl = new AuthenticationController(
                mock(AuthenticationManager.class),
                passwordEncoder,
                userRepo,
                userEmailLookupService,
                mock(JwtService.class),
                mock(PolicyService.class),
                testProps("AUTH_TOKEN"),
                mock(UserSessionService.class),
                mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class),
                null,
                mock(AuditLogService.class)
        );

        AuthenticationController.RegisterRequest req = new AuthenticationController.RegisterRequest();
        req.email = "newuser@example.com";
        req.password = "securePass1!";
        req.givenName = "New";
        req.familyName = "User";

        var res = ctrl.register(req, new MockHttpServletRequest("POST", "/auth/register"));

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("ok"));
    }

    @Test
    void registerWithExistingEmailReturns409() {
        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        User existing = new User();
        existing.setId(UUID.randomUUID());
        when(userEmailLookupService.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        AuthenticationController ctrl = new AuthenticationController(
                mock(AuthenticationManager.class),
                mock(PasswordEncoder.class),
                mock(UserRepository.class),
                userEmailLookupService,
                mock(JwtService.class),
                mock(PolicyService.class),
                testProps("AUTH_TOKEN"),
                mock(UserSessionService.class),
                mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class),
                null,
                mock(AuditLogService.class)
        );

        AuthenticationController.RegisterRequest req = new AuthenticationController.RegisterRequest();
        req.email = "existing@example.com";
        req.password = "somepassword";

        var res = ctrl.register(req, new MockHttpServletRequest("POST", "/auth/register"));

        assertEquals(409, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals("ERROR_EMAIL_EXISTS", body.get("error"));
    }

    @Test
    void registerDoesNotSaveWhenEmailAlreadyExists() {
        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        User existing = new User();
        existing.setId(UUID.randomUUID());
        when(userEmailLookupService.findByEmail("dupe@example.com")).thenReturn(Optional.of(existing));

        UserRepository userRepo = mock(UserRepository.class);

        AuthenticationController ctrl = new AuthenticationController(
                mock(AuthenticationManager.class),
                mock(PasswordEncoder.class),
                userRepo,
                userEmailLookupService,
                mock(JwtService.class),
                mock(PolicyService.class),
                testProps("AUTH_TOKEN"),
                mock(UserSessionService.class),
                mock(UserTenantMembershipRepository.class),
                mock(TenantAccessService.class),
                null,
                mock(AuditLogService.class)
        );

        AuthenticationController.RegisterRequest req = new AuthenticationController.RegisterRequest();
        req.email = "dupe@example.com";
        req.password = "somepassword";

        ctrl.register(req, new MockHttpServletRequest("POST", "/auth/register"));

        verify(userRepo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Příznak rememberMe
    // -------------------------------------------------------------------------

    @Test
    void loginWithRememberMeUsesLongerExpiry() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u", "p"));

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");

        UserEmailLookupService userEmailLookupService = mock(UserEmailLookupService.class);
        when(userEmailLookupService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of(), List.of(), 1));

        JwtService jwtService = mock(JwtService.class);
        String fakeJwt = "fake.jwt.token";
        when(jwtService.createAccessToken(anyString(), anyInt(), anyList(), anyList(), anyInt(), anyMap()))
                .thenReturn(fakeJwt);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .build();
        when(jwtService.parseAndVerify(fakeJwt)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.create(eq(user), anyString(), any(), any(), any(), any(),
                eq(true), isNull(), any())).thenReturn(stubSession(user, "jti"));

        UserTenantMembershipRepository membershipRepo = mock(UserTenantMembershipRepository.class);
        when(membershipRepo.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        AppProps props = testProps("AUTH_TOKEN");

        AuthenticationController ctrl = controller(authManager, userEmailLookupService,
                mock(UserRepository.class), jwtService, policyService, props, sessionService,
                membershipRepo, mock(TenantAccessService.class));

        AuthenticationController.LoginRequest req = new AuthenticationController.LoginRequest();
        req.email = "alice@example.com";
        req.password = "secret";
        req.rememberMe = true;

        var res = ctrl.login(req, new MockHttpServletRequest("POST", "/auth/login"));

        assertEquals(200, res.getStatusCode().value());
        // rememberMe = true => použije jwtRememberTtlMinutes
        verify(props).jwtRememberTtlMinutes();
    }
}
