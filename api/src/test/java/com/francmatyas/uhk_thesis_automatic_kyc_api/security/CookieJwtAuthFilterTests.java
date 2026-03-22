package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CookieJwtAuthFilterTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Existující testy (ponechány beze změny)
    // -------------------------------------------------------------------------

    @Test
    void authoritiesAreBuiltFromPolicySnapshotInsteadOfJwtPermissionClaim() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String jwtCookieName = "access_token";
        String token = "jwt-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .claim("permissions", List.of("provider.users:read"))
                .build();

        when(jwtService.parseAndVerify(token)).thenReturn(claims);
        when(sessionService.isActive(jti)).thenReturn(true);

        User user = new User();
        user.setId(userId);
        user.setProviderUser(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(
                        userId,
                        List.of("OWNER"),
                        List.of("provider.tenants:read"),
                        1
                )
        );

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);

        Set<String> authorities = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_OWNER"));
        assertTrue(authorities.contains("PERM_provider.tenants:read"));
        assertFalse(authorities.contains("PERM_provider.users:read"));
        verify(policyService, times(1)).buildForUser(userId);
        verify(sessionService, times(1)).touch(jti);
    }

    @Test
    void tenantHeaderTakesPrecedenceOverTenantClaimWhenBuildingPolicy() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantFromHeader = UUID.randomUUID();
        UUID tenantFromClaim = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String jwtCookieName = "access_token";
        String token = "jwt-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .claim("tenantId", tenantFromClaim.toString())
                .build();

        when(jwtService.parseAndVerify(token)).thenReturn(claims);
        when(sessionService.isActive(jti)).thenReturn(true);

        User user = new User();
        user.setId(userId);
        user.setProviderUser(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        when(policyService.buildForUser(userId)).thenAnswer(invocation -> {
            assertEquals(tenantFromHeader, TenantContext.getTenantId());
            return new PolicyService.PolicySnapshot(userId, List.of(), List.of(), 1);
        });

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        req.addHeader(TenantContextFilter.TENANT_HEADER, tenantFromHeader.toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(policyService, times(1)).buildForUser(userId);
        assertNull(TenantContext.getTenantId());
    }

    // -------------------------------------------------------------------------
    // Nové: chybové scénáře
    // -------------------------------------------------------------------------

    @Test
    void noCookieLeavesSecurityContextUnauthenticated() throws Exception {
        String jwtCookieName = "access_token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        // Nejsou nastavené cookies
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "No cookie should leave the security context unauthenticated");
        verifyNoInteractions(jwtService, userRepo, policyService, sessionService);
    }

    @Test
    void wrongCookieNameLeavesSecurityContextUnauthenticated() throws Exception {
        String jwtCookieName = "access_token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie("wrong_cookie_name", "some-token")); // wrong name
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    void expiredJwtLeavesSecurityContextUnauthenticated() throws Exception {
        String jwtCookieName = "access_token";
        String token = "expired-jwt-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        // Simulace expirovaného tokenu — parseAndVerify vyhodí výjimku
        when(jwtService.parseAndVerify(token)).thenThrow(new RuntimeException("Token expired"));

        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // NESMÍ vyhodit výjimku; musí být pohlcena
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Expired JWT must not authenticate the request");
        verifyNoInteractions(userRepo, policyService, sessionService);
    }

    @Test
    void invalidSignatureJwtLeavesSecurityContextUnauthenticated() throws Exception {
        String jwtCookieName = "access_token";
        String token = "invalid-sig-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        when(jwtService.parseAndVerify(token)).thenThrow(new RuntimeException("Invalid signature"));

        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepo, policyService, sessionService);
    }

    @Test
    void revokedJtiLeavesSecurityContextUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String jwtCookieName = "access_token";
        String token = "revoked-session-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .build();
        when(jwtService.parseAndVerify(token)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.isActive(jti)).thenReturn(false); // session revoked

        UserRepository userRepo = mock(UserRepository.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Revoked JTI must not authenticate the request");
        verifyNoInteractions(userRepo, policyService);
        verify(sessionService, never()).touch(anyString());
    }

    @Test
    void tokenWithEmptyJtiLeavesSecurityContextUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        String jwtCookieName = "access_token";
        String token = "empty-jti-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        // JTI je prázdné/null
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                // bez .jwtID(...)
                .build();
        when(jwtService.parseAndVerify(token)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        UserRepository userRepo = mock(UserRepository.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Token without JTI must not authenticate the request");
        verifyNoInteractions(userRepo, policyService);
    }

    @Test
    void userNotFoundInRepoDespiteValidTokenLeavesUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String jwtCookieName = "access_token";
        String token = "valid-token-unknown-user";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .build();
        when(jwtService.parseAndVerify(token)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.isActive(jti)).thenReturn(true);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(userId)).thenReturn(Optional.empty()); // user deleted

        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/provider/tenants");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Deleted user must not be authenticated even with a valid token");
        verifyNoInteractions(policyService);
        verify(sessionService, never()).touch(anyString());
    }

    @Test
    void publicEndpointSkipsAuthFilterEntirely() throws Exception {
        String jwtCookieName = "access_token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        req.setServletPath("/auth/login");
        req.setCookies(new Cookie(jwtCookieName, "some-token"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // /auth/* je veřejná cesta — zpracování JWT se musí úplně přeskočit
        verifyNoInteractions(jwtService, userRepo, policyService, sessionService);
    }

    @Test
    void validTokenSetsAuthenticationWithCorrectSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String jwtCookieName = "access_token";
        String token = "valid-jwt-token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .build();
        when(jwtService.parseAndVerify(token)).thenReturn(claims);

        UserSessionService sessionService = mock(UserSessionService.class);
        when(sessionService.isActive(jti)).thenReturn(true);

        User user = new User();
        user.setId(userId);
        user.setProviderUser(false);
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        PolicyService policyService = mock(PolicyService.class);
        when(policyService.buildForUser(userId)).thenReturn(
                new PolicyService.PolicySnapshot(userId, List.of("MEMBER"), List.of("tenant.read"), 1));

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/some-resource");
        req.setCookies(new Cookie(jwtCookieName, token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Valid token should authenticate the request");
        assertInstanceOf(JwtPrincipal.class, auth);
        assertEquals(userId.toString(), ((JwtPrincipal) auth).getSubject());

        Set<String> authorityStrings = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
        assertTrue(authorityStrings.contains("ROLE_MEMBER"));
        assertTrue(authorityStrings.contains("PERM_tenant.read"));
    }

    @Test
    void filterDoesNotOverrideExistingAuthentication() throws Exception {
        String jwtCookieName = "access_token";

        AppProps props = mock(AppProps.class);
        when(props.jwtCookieName()).thenReturn(jwtCookieName);

        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepo = mock(UserRepository.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        PolicyService policyService = mock(PolicyService.class);

        // Přednastavení existující autentizace v kontextu
        UUID existingUserId = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setId(existingUserId);
        JwtPrincipal existingAuth = new JwtPrincipal(existingUserId.toString(), existingUser,
                Set.of(), new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        CookieJwtAuthFilter filter = new CookieJwtAuthFilter(props, jwtService, userRepo, sessionService, policyService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/some-resource");
        req.setCookies(new Cookie(jwtCookieName, "some-token"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Autentizace už byla nastavená, takže filtr nemá volat jwtService
        verifyNoInteractions(jwtService);
        // Objekt autentizace má zůstat stejný, který jsme nastavili
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
    }
}
