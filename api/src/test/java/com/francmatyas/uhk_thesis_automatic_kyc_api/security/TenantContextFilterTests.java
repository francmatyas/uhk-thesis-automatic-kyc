package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

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

class TenantContextFilterTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // JWT principal — hlavička má prioritu
    // -------------------------------------------------------------------------

    @Test
    void prefersHeaderOverJwtClaim() throws Exception {
        UUID headerTenant = UUID.randomUUID();
        UUID jwtTenant = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("tenantId", jwtTenant.toString())
                .build();

        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(),
                new Object(),
                List.of(),
                claims
        );

        var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, headerTenant.toString());
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (req1, res1) -> {
            assertEquals(headerTenant, TenantContext.getTenantId());
        });
    }

    @Test
    void usesJwtClaimWhenHeaderMissing() throws Exception {
        UUID jwtTenant = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("tenantId", jwtTenant.toString())
                .build();

        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(),
                new Object(),
                List.of(),
                claims
        );

        var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (req1, res1) -> {
            assertEquals(jwtTenant, TenantContext.getTenantId());
        });
    }

    // -------------------------------------------------------------------------
    // Prázdné / nevalidní vstupy
    // -------------------------------------------------------------------------

    @Test
    void noAuthAndNoHeaderLeavesTenantNull() throws Exception {
        // V SecurityContext není nastavená autentizace
        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Po dokončení filtru je kontext vyčištěný — snapshot je null
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void malformedTenantHeaderFallsBackToJwtClaim() throws Exception {
        UUID jwtTenant = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("tenantId", jwtTenant.toString())
                .build();

        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(), new Object(), List.of(), claims);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, "not-a-valid-uuid"); // malformed
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertEquals(jwtTenant, observed[0],
                "Malformed header should fall back to JWT claim");
    }

    @Test
    void malformedJwtClaimTenantIdResultsInNullTenant() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("tenantId", "not-a-uuid") // invalid UUID in JWT claim
                .build();

        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(), new Object(), List.of(), claims);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertNull(observed[0], "Invalid UUID in JWT tenantId claim should result in null tenant");
    }

    @Test
    void emptyTenantHeaderFallsBackToJwtClaim() throws Exception {
        UUID jwtTenant = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("tenantId", jwtTenant.toString())
                .build();

        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(), new Object(), List.of(), claims);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, "   "); // blank/whitespace
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertEquals(jwtTenant, observed[0],
                "Blank header should fall back to JWT claim");
    }

    @Test
    void jwtPrincipalWithNullClaimsLeavesTenantNull() throws Exception {
        JwtPrincipal principal = new JwtPrincipal(
                UUID.randomUUID().toString(), new Object(), List.of(), null);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(principal);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertNull(observed[0]);
    }

    @Test
    void tenantContextIsClearedAfterFilterCompletes() throws Exception {
        UUID headerTenant = UUID.randomUUID();

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, headerTenant.toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Po dokončení filtru musí finally blok vyčistit TenantContext
        assertNull(TenantContext.getTenantId(),
                "TenantContext must be cleared after filter chain completes");
    }

    // -------------------------------------------------------------------------
    // Cesta pro API key principal
    // -------------------------------------------------------------------------

    @Test
    void apiKeyPrincipalSetsTenantFromApiKey() throws Exception {
        UUID apiKeyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ApiKeyPrincipal apiKeyPrincipal = new ApiKeyPrincipal(apiKeyId, tenantId, "CI key", "pk_test");

        // Simulace autentizačního tokenu s ApiKeyPrincipal jako principalem
        org.springframework.security.authentication.AbstractAuthenticationToken auth =
                new org.springframework.security.authentication.AbstractAuthenticationToken(
                        Set.of(new SimpleGrantedAuthority("ROLE_API_KEY"))) {
                    @Override public Object getCredentials() { return ""; }
                    @Override public Object getPrincipal() { return apiKeyPrincipal; }
                };
        auth.setAuthenticated(true);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertEquals(tenantId, observed[0],
                "API key auth should set tenant from the bound api key's tenantId");
    }

    @Test
    void apiKeyPrincipalWithMatchingHeaderPassesThrough() throws Exception {
        UUID apiKeyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ApiKeyPrincipal apiKeyPrincipal = new ApiKeyPrincipal(apiKeyId, tenantId, "CI key", "pk_test");

        org.springframework.security.authentication.AbstractAuthenticationToken auth =
                new org.springframework.security.authentication.AbstractAuthenticationToken(
                        Set.of(new SimpleGrantedAuthority("ROLE_API_KEY"))) {
                    @Override public Object getCredentials() { return ""; }
                    @Override public Object getPrincipal() { return apiKeyPrincipal; }
                };
        auth.setAuthenticated(true);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, tenantId.toString()); // same tenant
        MockHttpServletResponse res = new MockHttpServletResponse();

        final UUID[] observed = new UUID[1];
        filter.doFilter(req, res, (r, s) -> observed[0] = TenantContext.getTenantId());

        assertEquals(tenantId, observed[0]);
        assertEquals(200, res.getStatus(), "Response should not be blocked with matching tenant header");
    }

    @Test
    void apiKeyPrincipalWithMismatchedTenantHeaderReturns403() throws Exception {
        UUID apiKeyId = UUID.randomUUID();
        UUID boundTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        ApiKeyPrincipal apiKeyPrincipal = new ApiKeyPrincipal(apiKeyId, boundTenant, "CI key", "pk_test");

        org.springframework.security.authentication.AbstractAuthenticationToken auth =
                new org.springframework.security.authentication.AbstractAuthenticationToken(
                        Set.of(new SimpleGrantedAuthority("ROLE_API_KEY"))) {
                    @Override public Object getCredentials() { return ""; }
                    @Override public Object getPrincipal() { return apiKeyPrincipal; }
                };
        auth.setAuthenticated(true);

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TenantContextFilter.TENANT_HEADER, otherTenant.toString()); // DIFFERENT tenant
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("tenant_mismatch_for_api_key"));
    }
}
