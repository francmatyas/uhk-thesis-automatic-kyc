package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Vynucuje tenant členství pro provider uživatele, pokud je vybraný aktivní tenant.
 *
 * Kontrakt:
 *  - Pokud není vybraný tenant (TenantContext.tenantId == null): nic nedělej.
 *  - Pokud je tenant vybraný:
 *      - provider uživatel musí mít pro daného tenanta alespoň jeden řádek v UserTenantRole, jinak 403.
 *      - tok pro tenant uživatele zde zůstává beze změny.
 */
@Component
@RequiredArgsConstructor
public class ProviderTenantAccessFilter extends OncePerRequestFilter {

    private final UserTenantRoleRepository userTenantRoleRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtPrincipal jp)) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = jp.getPrincipal();
        if (!(principal instanceof User u)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!u.isProviderUser()) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean member = userTenantRoleRepository.existsByUserIdAndTenantId(u.getId(), tenantId);
        if (!member) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"not_member_of_tenant\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
