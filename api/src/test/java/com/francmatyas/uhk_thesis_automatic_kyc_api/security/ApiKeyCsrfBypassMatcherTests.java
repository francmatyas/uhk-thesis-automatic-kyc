package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCsrfBypassMatcherTests {

    @Test
    void matchesWhenApiKeyHeadersPresentAndNoJwtCookie() {
        RequestMatcher matcher = matcher("access_token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/switch");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "pk_live_123");
        request.addHeader(ApiKeyAuthFilter.API_SECRET_HEADER, "sk_live_123");

        assertTrue(matcher.matches(request));
    }

    @Test
    void doesNotMatchWhenSecretMissingOrBlank() {
        RequestMatcher matcher = matcher("access_token");
        MockHttpServletRequest missingSecret = new MockHttpServletRequest("POST", "/tenant/switch");
        missingSecret.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "pk_live_123");
        assertFalse(matcher.matches(missingSecret));

        MockHttpServletRequest blankSecret = new MockHttpServletRequest("POST", "/tenant/switch");
        blankSecret.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "pk_live_123");
        blankSecret.addHeader(ApiKeyAuthFilter.API_SECRET_HEADER, "   ");
        assertFalse(matcher.matches(blankSecret));
    }

    @Test
    void doesNotMatchWhenJwtCookieIsPresent() {
        RequestMatcher matcher = matcher("access_token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant/switch");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "pk_live_123");
        request.addHeader(ApiKeyAuthFilter.API_SECRET_HEADER, "sk_live_123");
        request.setCookies(new Cookie("access_token", "jwt"));

        assertFalse(matcher.matches(request));
    }

    private RequestMatcher matcher(String jwtCookieName) {
        AppProps props = new AppProps();
        AppProps.Jwt jwt = new AppProps.Jwt();
        jwt.setCookieName(jwtCookieName);
        props.setJwt(jwt);

        SecurityConfig config = new SecurityConfig(null, null, null, null, null, null, null);
        return config.apiKeyCsrfBypassMatcher(props);
    }
}
