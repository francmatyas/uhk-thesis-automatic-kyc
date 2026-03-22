package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthRateLimitFilter authRateLimitFilter;
    private final CookieJwtAuthFilter cookieJwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;
    private final ProviderTenantAccessFilter providerTenantAccessFilter;
    private final ProviderOnlyPathFilter providerOnlyPathFilter;
    private final UserDetailsService userDetailsService;
    private final ApiAwareAuthenticationEntryPoint apiAwareAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            RequestMatcher apiKeyCsrfBypassMatcher,
            ApiKeyAuthFilter apiKeyAuthFilter
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(cookieCsrfTokenRepository())
                        .ignoringRequestMatchers(apiKeyCsrfBypassMatcher)
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight a chybové koncové body mají být veřejné
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Veřejné koncové body (auth, statické soubory a KYC klientský proces)
                        .requestMatchers("/auth/**", "/translations/**", "/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers("/flow/**").permitAll()
                        // Vše ostatní vyžaduje autentizaci; chybějící endpointy řeší ApiAwareAuthenticationEntryPoint
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAwareAuthenticationEntryPoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            if (accessDeniedException instanceof MissingCsrfTokenException
                                    || accessDeniedException instanceof InvalidCsrfTokenException) {
                                response.getWriter().write("{\"error\":\"csrf_invalid\"}");
                            } else {
                                response.getWriter().write("{\"error\":\"forbidden\"}");
                            }
                        })
                )
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(cookieJwtAuthFilter, ApiKeyAuthFilter.class)
                .addFilterAfter(providerOnlyPathFilter, CookieJwtAuthFilter.class)
                .addFilterAfter(tenantContextFilter, CookieJwtAuthFilter.class)
                .addFilterAfter(providerTenantAccessFilter, TenantContextFilter.class);

        return http.build();
    }

    @Bean
    public RequestMatcher apiKeyCsrfBypassMatcher(AppProps props) {
        return request -> {
            String apiKey = request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER);
            String apiSecret = request.getHeader(ApiKeyAuthFilter.API_SECRET_HEADER);
            boolean hasApiKeyHeaders = apiKey != null && !apiKey.isBlank()
                    && apiSecret != null && !apiSecret.isBlank();
            if (!hasApiKeyHeaders) {
                return false;
            }

            String jwtCookieName = props.jwtCookieName();
            if (jwtCookieName == null || jwtCookieName.isBlank()) {
                return true;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return true;
            }
            for (Cookie c : cookies) {
                if (jwtCookieName.equals(c.getName())) {
                    return false;
                }
            }
            return true;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        repo.setCookieName("XSRF-TOKEN");
        repo.setHeaderName("X-XSRF-TOKEN");
        repo.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return repo;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    private UrlBasedCorsConfigurationSource buildCorsSource(AppProps props) {
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = props.corsAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            if (props.isProd()) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGINS must be configured in production");
            }
            origins = new ArrayList<>();
            origins.add("http://localhost:5173");
        }
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.addAllowedHeader("*");
        cfg.setAllowCredentials(true);
        cfg.addExposedHeader("Location");
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProps props) {
        return buildCorsSource(props);
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(AppProps props) {
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(buildCorsSource(props)));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
