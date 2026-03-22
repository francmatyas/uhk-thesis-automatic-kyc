package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_SECRET_HEADER = "X-API-Secret";

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        String publicKey = req.getHeader(API_KEY_HEADER);
        String secret = req.getHeader(API_SECRET_HEADER);
        String path = req.getServletPath();

        boolean hasPublicKey = publicKey != null && !publicKey.isBlank();
        boolean hasSecret = secret != null && !secret.isBlank();

        if (!hasPublicKey && !hasSecret) {
            chain.doFilter(req, res);
            return;
        }

        if (isBlacklistedPath(path)) {
            forbidden(res, "api_key_forbidden_path");
            return;
        }

        if (!hasPublicKey || !hasSecret) {
            unauthorized(res, "invalid_api_key_credentials");
            return;
        }

        var principalOpt = apiKeyAuthenticationService.authenticate(publicKey, secret);
        if (principalOpt.isEmpty()) {
            unauthorized(res, "invalid_api_key_credentials");
            return;
        }

        var principal = principalOpt.get();
        AbstractAuthenticationToken auth = new AbstractAuthenticationToken(List.of(
                new SimpleGrantedAuthority("ROLE_API_KEY")
        )) {
            @Override
            public Object getCredentials() {
                return "";
            }

            @Override
            public Object getPrincipal() {
                return principal;
            }

            @Override
            public String getName() {
                return principal.apiKeyId().toString();
            }
        };
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }

    private void unauthorized(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"unauthorized\",\"reason\":\"" + reason + "\"}");
    }

    private void forbidden(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"" + reason + "\"}");
    }

    private boolean isBlacklistedPath(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("/users")
                || path.startsWith("/users/")
                || path.equals("/auth")
                || path.startsWith("/auth/");
    }
}
