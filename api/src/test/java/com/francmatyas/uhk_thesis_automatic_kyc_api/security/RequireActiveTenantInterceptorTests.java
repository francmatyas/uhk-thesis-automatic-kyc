package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequireActiveTenantInterceptorTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @RequireActiveTenant
    static class AnnotatedController {
        public void handler() {
        }

        @RequireActiveTenant
        public void annotatedHandler() {
        }

        public void plainHandler() {
        }
    }

    static class NonAnnotatedController {
        public void plainHandler() {
        }
    }

    @Test
    void annotatedHandlerWithoutTenantReturns400() throws Exception {
        RequireActiveTenantInterceptor interceptor = new RequireActiveTenantInterceptor();

        HandlerMethod hm = new HandlerMethod(new AnnotatedController(), AnnotatedController.class.getMethod("annotatedHandler"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);

        assertFalse(ok);
        assertEquals(400, res.getStatus());
        assertTrue(res.getContentAsString().contains("tenant_required"));
    }

    @Test
    void annotatedHandlerWithTenantPasses() throws Exception {
        TenantContext.setTenantId(java.util.UUID.randomUUID());
        RequireActiveTenantInterceptor interceptor = new RequireActiveTenantInterceptor();

        HandlerMethod hm = new HandlerMethod(new AnnotatedController(), AnnotatedController.class.getMethod("annotatedHandler"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertTrue(ok);
    }

    @Test
    void nonAnnotatedHandlerPassesWithoutTenant() throws Exception {
        RequireActiveTenantInterceptor interceptor = new RequireActiveTenantInterceptor();

        HandlerMethod hm = new HandlerMethod(new NonAnnotatedController(), NonAnnotatedController.class.getMethod("plainHandler"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/whatever");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertTrue(ok);
    }

    @Test
    void annotatedHandlerWithoutTenantAllowsProviderUser() throws Exception {
        User provider = mock(User.class);
        when(provider.isProviderUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(provider, null)
        );

        RequireActiveTenantInterceptor interceptor = new RequireActiveTenantInterceptor();

        HandlerMethod hm = new HandlerMethod(new AnnotatedController(), AnnotatedController.class.getMethod("annotatedHandler"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/simulations");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);

        assertTrue(ok);
    }
}
