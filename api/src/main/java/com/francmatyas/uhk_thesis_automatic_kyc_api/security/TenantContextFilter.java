package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.UUID;

/**
 * Odvozuje aktivního tenanta pro request. Pořadí vyhodnocení: (1) hlavička X-Tenant-Id (UUID),
 * (2) JWT údaj "tenantId" (pokud existuje), (3) null. Provider uživatelé mohou tenantId vynechat;
 * tenant uživatelé by ho měli poskytnout.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof ApiKeyPrincipal ap) {
                UUID tenantId = ap.tenantId();
                String requestedTenantHeader = request.getHeader(TENANT_HEADER);
                if (requestedTenantHeader != null && !requestedTenantHeader.isBlank()) {
                    try {
                        UUID requestedTenant = UUID.fromString(requestedTenantHeader.trim());
                        if (!tenantId.equals(requestedTenant)) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"tenant_mismatch_for_api_key\"}");
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // ignorovat neplatnou tenant hlavičku u API key volání a pokračovat s navázaným tenantem
                    }
                }
                TenantContext.setTenantId(tenantId);
                filterChain.doFilter(request, response);
                return;
            }

            UUID tenantId = null;

            String header = request.getHeader(TENANT_HEADER);
            if (header != null && !header.isBlank()) {
                try {
                    tenantId = UUID.fromString(header.trim());
                } catch (IllegalArgumentException ignored) {
                    // ignorovat neplatnou hlavičku; zůstává null
                }
            }

            if (tenantId == null) {
                if (auth instanceof JwtPrincipal jp) {
                    Object claim = jp.getClaims() != null ? jp.getClaims().getClaim("tenantId") : null;
                    if (claim instanceof String s && !s.isBlank()) {
                        try {
                            tenantId = UUID.fromString(s);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }

            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
