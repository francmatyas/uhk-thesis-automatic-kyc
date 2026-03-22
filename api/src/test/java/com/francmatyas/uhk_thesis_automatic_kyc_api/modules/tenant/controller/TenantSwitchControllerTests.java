package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.AppProps;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.JwtService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.PolicyService;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantSwitchControllerTests {

    @Test
    void resolveTenantBySlugReturnsTenantWhenUserCanAccess() {
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PolicyService policyService = mock(PolicyService.class);
        JwtService jwtService = mock(JwtService.class);
        AppProps props = mock(AppProps.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        TenantSwitchController controller = new TenantSwitchController(
                tenantAccessService, policyService, jwtService, props, sessionService, auditLogService, null
        );

        User user = new User();
        user.setId(UUID.randomUUID());

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Acme");
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.findTenantBySlug("acme")).thenReturn(Optional.of(tenant));
        when(tenantAccessService.canAccessTenant(user, tenant.getId())).thenReturn(true);

        var res = controller.resolveTenant(user, "acme");

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals(tenant.getId(), body.get("id"));
        assertEquals("acme", body.get("slug"));
    }

    @Test
    void switchTenantBySlugMintsTokenAndReturnsTenant() {
        TenantAccessService tenantAccessService = mock(TenantAccessService.class);
        PolicyService policyService = mock(PolicyService.class);
        JwtService jwtService = mock(JwtService.class);
        AppProps props = mock(AppProps.class);
        UserSessionService sessionService = mock(UserSessionService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        TenantSwitchController controller = new TenantSwitchController(
                tenantAccessService, policyService, jwtService, props, sessionService, auditLogService, null
        );

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setProviderUser(false);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Acme");
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantAccessService.findTenantBySlug("acme")).thenReturn(Optional.of(tenant));
        when(tenantAccessService.canAccessTenant(user, tenant.getId())).thenReturn(true);
        when(tenantAccessService.findTenant(tenant.getId())).thenReturn(Optional.of(tenant));
        when(policyService.buildForUser(user.getId()))
                .thenReturn(new PolicyService.PolicySnapshot(user.getId(), List.of("TENANT_ADMIN"), List.of("users:read"), 1));

        when(props.jwtAccessTtlMinutes()).thenReturn(120);
        when(props.jwtRememberTtlMinutes()).thenReturn(43200);
        when(props.jwtCookieName()).thenReturn("access_token");
        when(props.jwtSameSite()).thenReturn("Lax");
        when(props.isProd()).thenReturn(false);

        when(jwtService.createAccessToken(any(), any(Integer.class), any(), any(), any(Integer.class), any()))
                .thenReturn("new-token");
        when(jwtService.parseAndVerify("new-token")).thenReturn(new JWTClaimsSet.Builder()
                .jwtID("jti-1")
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(3600)))
                .build());

        UserSession session = new UserSession();
        when(sessionService.rotateOrCreateForSwitch(any(), any(), any(), any(), any(), any(), any(), any(Boolean.class), any(), any())).thenReturn(session);

        TenantSwitchController.SwitchTenantRequest req = new TenantSwitchController.SwitchTenantRequest();
        req.tenantSlug = "acme";

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/tenants/switch");
        var res = controller.switchTenant(user, req, httpReq);

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);
        assertEquals(tenant.getId(), body.get("activeTenantId"));
    }

}
