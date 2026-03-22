package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CorsPreflightTest.DummyController.class)
@Import({SecurityConfig.class, CorsPreflightTest.TestBeans.class})
class CorsPreflightTest {

    @Autowired
    MockMvc mockMvc;

    // Namockovat beany filtrů, aby je SecurityConfig získal bez transitivních závislostí
    @MockBean ApiKeyAuthFilter apiKeyAuthFilter;
    @MockBean CookieJwtAuthFilter cookieJwtAuthFilter;
    @MockBean AuthRateLimitFilter authRateLimitFilter;
    @MockBean TenantContextFilter tenantContextFilter;
    @MockBean ProviderTenantAccessFilter providerTenantAccessFilter;
    @MockBean ProviderOnlyPathFilter providerOnlyPathFilter;
    @MockBean UserDetailsService userDetailsService;
    @MockBean ApiAwareAuthenticationEntryPoint apiAwareAuthenticationEntryPoint;
    @MockBean JwtService jwtService;
    @MockBean PolicyService policyService;

    /** Minimální kontroler — jen aby měl WebMvcTest co načíst. */
    @RestController
    @RequestMapping("/test")
    static class DummyController {
        @GetMapping  String get()    { return "ok"; }
        @PostMapping String post()   { return "ok"; }
        @DeleteMapping String delete() { return "ok"; }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        AppProps appProps() {
            AppProps p = new AppProps();
            p.getCors().setAllowedOrigins(List.of("http://localhost:5173"));
            p.getJwt().setCookieName("AUTH_TOKEN");
            p.getJwt().setSameSite("Lax");
            p.getJwt().setIssuer("automatic-kyc-api");
            p.getJwt().setSecret("testsecret");
            p.getJwt().setAccessTtlMinutes(60);
            return p;
        }
    }

    @Test
    void preflightRequestFromAllowedOriginReturns200() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk());
    }

    @Test
    void preflightResponseIncludesAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void preflightResponseAllowsCredentials() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightResponseAllowsPostMethod() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void preflightFromDisallowedOriginDoesNotEchoOrigin() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightResponseIncludesMaxAgeHeader() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
    }

    @Test
    void preflightForDeleteMethodIsAllowed() throws Exception {
        mockMvc.perform(options("/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
