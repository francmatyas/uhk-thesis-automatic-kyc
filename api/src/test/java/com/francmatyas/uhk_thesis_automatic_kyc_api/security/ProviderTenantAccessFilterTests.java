package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderTenantAccessFilterTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Existující procházející testy (ponecháno, jen doplněné drobné aserce na body)
    // -------------------------------------------------------------------------

    @Test
    void providerUserWithTenantButNotMemberGets403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u, Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")), new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        when(repo.existsByUserIdAndTenantId(userId, tenantId)).thenReturn(false);

        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("not_member_of_tenant"));

        verify(repo, times(1)).existsByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    void providerUserWithTenantAndMemberPassesThrough() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u, Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")), new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        when(repo.existsByUserIdAndTenantId(userId, tenantId)).thenReturn(true);

        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertTrue(res.getStatus() == 0 || res.getStatus() == 200);
        verify(repo, times(1)).existsByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    void providerUserWithoutTenantPassesThroughAndDoesNotQueryRepo() throws Exception {
        UUID userId = UUID.randomUUID();

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u, Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")), new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(repo);
    }

    @Test
    void tenantUserWithTenantPassesThroughAndDoesNotQueryRepo() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(false);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u, Set.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")), new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(repo);
    }

    // -------------------------------------------------------------------------
    // Nové: null / hraniční scénáře
    // -------------------------------------------------------------------------

    @Test
    void noAuthenticationPassesThroughWithTenantSet() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        // V SecurityContext není autentizace

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Bez autentizace filtr přeskočí kontrolu membership
        verifyNoInteractions(repo);
        assertTrue(res.getStatus() == 0 || res.getStatus() == 200,
                "Filter should pass through when there is no authentication");
    }

    @Test
    void nonJwtPrincipalAuthPassesThroughWithTenantSet() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // Použít autentizaci, která není JwtPrincipal (např. API key auth)
        org.springframework.security.authentication.AbstractAuthenticationToken auth =
                new org.springframework.security.authentication.AbstractAuthenticationToken(
                        Set.of(new SimpleGrantedAuthority("ROLE_API_KEY"))) {
                    @Override public Object getCredentials() { return ""; }
                    @Override public Object getPrincipal() { return new ApiKeyPrincipal(UUID.randomUUID(), tenantId, "key", "pk_test"); }
                };
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(repo);
    }

    @Test
    void jwtPrincipalWithNonUserPrincipalObjectPassesThrough() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // JwtPrincipal s principal objektem, který není User
        JwtPrincipal auth = new JwtPrincipal(
                UUID.randomUUID().toString(),
                "some-non-user-principal-string", // not a User instance
                Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")),
                new JWTClaimsSet.Builder().build()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(repo);
        assertTrue(res.getStatus() == 0 || res.getStatus() == 200);
    }

    @Test
    void repoExceptionPropagatesAsServletException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u,
                Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")),
                new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        when(repo.existsByUserIdAndTenantId(userId, tenantId))
                .thenThrow(new RuntimeException("DB connection lost"));

        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // Filtr výjimky nepohlcuje — propagují se dál
        assertThrows(Exception.class, () -> filter.doFilter(req, res, chain));
    }

    @Test
    void providerUserNotMemberResponseContainsErrorAndReasonFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u,
                Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")),
                new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        when(repo.existsByUserIdAndTenantId(userId, tenantId)).thenReturn(false);

        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        String body = res.getContentAsString();
        assertTrue(body.contains("\"error\""), "Response body must contain error field");
        assertTrue(body.contains("\"reason\""), "Response body must contain reason field");
        assertTrue(body.contains("not_member_of_tenant"), "Reason must be 'not_member_of_tenant'");
        assertEquals("application/json", res.getContentType(),
                "Error response must be application/json");
    }

    @Test
    void providerUserMembershipQueryUsesCorrectUserAndTenantIds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        JwtPrincipal auth = new JwtPrincipal(userId.toString(), u,
                Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_ADMIN")),
                new JWTClaimsSet.Builder().build());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserTenantRoleRepository repo = mock(UserTenantRoleRepository.class);
        when(repo.existsByUserIdAndTenantId(userId, tenantId)).thenReturn(true);

        ProviderTenantAccessFilter filter = new ProviderTenantAccessFilter(repo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Ověřit, že repository bylo volané přesně se správnými ID
        verify(repo, times(1)).existsByUserIdAndTenantId(userId, tenantId);
        verifyNoMoreInteractions(repo);
    }
}
