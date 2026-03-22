package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyAccessInterceptorTests {

    static class PlainController {
        public void plain() {
        }
    }

    @ApiKeyAccessible
    static class ApiController {
        public void apiOk() {
        }

        public void apiNeedsUser(@AuthenticationPrincipal User user) {
        }

        public void apiNeedsJwt(@AuthenticationPrincipal JwtPrincipal principal) {
        }
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiKeyPrincipalWithoutAnnotationIsForbidden() throws Exception {
        ApiKeyAccessInterceptor interceptor = new ApiKeyAccessInterceptor();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID(), "key", "pk_test"),
                        null
                )
        );

        HandlerMethod hm = new HandlerMethod(new PlainController(), PlainController.class.getMethod("plain"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/plain");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertFalse(ok);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("api_key_endpoint_not_enabled"));
    }

    @Test
    void apiKeyPrincipalWithAnnotationPasses() throws Exception {
        ApiKeyAccessInterceptor interceptor = new ApiKeyAccessInterceptor();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID(), "key", "pk_test"),
                        null
                )
        );

        HandlerMethod hm = new HandlerMethod(new ApiController(), ApiController.class.getMethod("apiOk"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api-ok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertTrue(ok);
    }

    @Test
    void apiKeyPrincipalWithUserPrincipalParameterIsForbidden() throws Exception {
        ApiKeyAccessInterceptor interceptor = new ApiKeyAccessInterceptor();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID(), "key", "pk_test"),
                        null
                )
        );

        HandlerMethod hm = new HandlerMethod(new ApiController(), ApiController.class.getMethod("apiNeedsUser", User.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api-user");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertFalse(ok);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("api_key_not_supported_for_endpoint"));
    }

    @Test
    void apiKeyPrincipalWithUnsupportedPrincipalParameterIsForbidden() throws Exception {
        ApiKeyAccessInterceptor interceptor = new ApiKeyAccessInterceptor();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID(), "key", "pk_test"),
                        null
                )
        );

        HandlerMethod hm = new HandlerMethod(new ApiController(), ApiController.class.getMethod("apiNeedsJwt", JwtPrincipal.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, hm);
        assertFalse(ok);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("api_key_principal_type_unsupported"));
    }
}
