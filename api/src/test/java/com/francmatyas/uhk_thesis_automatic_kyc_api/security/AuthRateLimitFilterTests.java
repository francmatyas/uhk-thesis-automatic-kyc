package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy filtru pro omezení počtu požadavků podle IP, který chrání POST /auth/login
 * a POST /auth/register proti brute-force pokusům.
 */
class AuthRateLimitFilterTests {

    private static MockHttpServletRequest postRequest(String path, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRequestURI(path);
        req.setRemoteAddr(ip);
        return req;
    }

    // -------------------------------------------------------------------------
    // shouldNotFilter — cesty, které se mají přeskočit
    // -------------------------------------------------------------------------

    @Test
    void getRequestsAreNotFiltered() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/login");
        req.setRequestURI("/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // GET se nefiltruje — má projít bez spotřeby tokenu
        assertNotEquals(429, res.getStatus(), "GET requests must not be rate-limited");
    }

    @Test
    void otherPathsAreNotFiltered() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/provider/users");
        req.setRequestURI("/provider/users");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotEquals(429, res.getStatus(), "Requests to non-rate-limited paths must pass through");
    }

    // -------------------------------------------------------------------------
    // Omezení počtu login pokusů
    // -------------------------------------------------------------------------

    @Test
    void loginRequestWithinLimitPassesThrough() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();

        MockHttpServletRequest req = postRequest("/auth/login", "192.168.1.100");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotEquals(429, res.getStatus(), "First login request must not be rate-limited");
    }

    @Test
    void loginRequestsExceedingLimitReturn429() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String ip = "192.168.1.50";

        // Vyčerpání limitu 10 pokusů z této IP
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = postRequest("/auth/login", ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
        }

        // 11. pokus má být omezen limitem
        MockHttpServletRequest req11 = postRequest("/auth/login", ip);
        MockHttpServletResponse res11 = new MockHttpServletResponse();
        MockFilterChain chain11 = new MockFilterChain();

        filter.doFilter(req11, res11, chain11);

        assertEquals(429, res11.getStatus(), "11th login request from same IP must be rate-limited");
    }

    @Test
    void rateLimitResponseIncludesRetryAfterHeader() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String ip = "192.168.1.51";

        // Vyčerpání limitu
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = postRequest("/auth/login", ip);
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest req = postRequest("/auth/login", ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"),
                "429 response must include Retry-After header");
        assertEquals("application/json", res.getContentType(),
                "429 response must be JSON");
    }

    @Test
    void rateLimitResponseBodyContainsErrorField() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String ip = "10.0.1.1";

        for (int i = 0; i < 10; i++) {
            filter.doFilter(postRequest("/auth/login", ip), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(postRequest("/auth/login", ip), res, new MockFilterChain());

        assertEquals(429, res.getStatus());
        String body = res.getContentAsString();
        assertTrue(body.contains("too_many_requests"),
                "429 body must contain 'too_many_requests': " + body);
    }

    // -------------------------------------------------------------------------
    // Omezení počtu register pokusů
    // -------------------------------------------------------------------------

    @Test
    void registerRequestWithinLimitPassesThrough() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();

        MockHttpServletRequest req = postRequest("/auth/register", "172.16.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotEquals(429, res.getStatus(), "First register request must not be rate-limited");
    }

    @Test
    void registerRequestsExceedingLimitReturn429() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String ip = "172.16.0.10";

        for (int i = 0; i < 10; i++) {
            filter.doFilter(postRequest("/auth/register", ip), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse res11 = new MockHttpServletResponse();
        filter.doFilter(postRequest("/auth/register", ip), res11, new MockFilterChain());

        assertEquals(429, res11.getStatus(), "11th register request from same IP must be rate-limited");
    }

    // -------------------------------------------------------------------------
    // IP izolace — různé IP mají nezávislé buckety
    // -------------------------------------------------------------------------

    @Test
    void differentIpsHaveIndependentRateLimits() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String ipA = "203.0.113.1";
        String ipB = "203.0.113.2";

        // Vyčerpání limitu pro ipA
        for (int i = 0; i < 10; i++) {
            filter.doFilter(postRequest("/auth/login", ipA), new MockHttpServletResponse(), new MockFilterChain());
        }

        // ipB má být stále v limitu
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(postRequest("/auth/login", ipB), resB, new MockFilterChain());

        assertNotEquals(429, resB.getStatus(),
                "ipB should not be rate-limited even if ipA is exhausted");
    }

    // -------------------------------------------------------------------------
    // Hlavička Cloudflare IP má prioritu
    // -------------------------------------------------------------------------

    @Test
    void cfConnectingIpIsUsedForRateLimiting() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        String cfIp = "1.2.3.4";

        // Vyčerpání limitu přes hlavičku CF-Connecting-IP
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = postRequest("/auth/login", "192.168.100.1");
            req.addHeader("CF-Connecting-IP", cfIp);
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // Další pokus se stejnou CF IP má být blokován
        MockHttpServletRequest req11 = postRequest("/auth/login", "192.168.100.1"); // different TCP addr
        req11.addHeader("CF-Connecting-IP", cfIp); // same CF IP
        MockHttpServletResponse res11 = new MockHttpServletResponse();
        filter.doFilter(req11, res11, new MockFilterChain());

        assertEquals(429, res11.getStatus(),
                "Rate limit should apply to the CF-Connecting-IP value, not the TCP address");
    }
}
