package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CookieJwtAuthFilter extends OncePerRequestFilter {

    private final AppProps props;
    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final UserSessionService sessionService;
    private final PolicyService policyService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getServletPath();

        // Přeskočit JWT autentizaci pro veřejné koncové body a chybový dispatch
        if (path.startsWith("/auth/") ||
                path.startsWith("/public/") ||
                path.startsWith("/translations/") ||
                path.startsWith("/images/") ||
                path.equals("/health") ||
                path.equals("/error")
        ) {
            chain.doFilter(req, res);
            return;
        }

        String token = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (props.jwtCookieName().equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JWTClaimsSet claims = jwtService.parseAndVerify(token);

                // Kontrola perzistentního sezení (revokace)
                String jti = JwtService.getJti(claims);
                if (jti == null || jti.isBlank()) {
                    chain.doFilter(req, res);
                    return;
                }

                if (!sessionService.isActive(jti)) {
                    // Sezení je revokované nebo expirované -> neautentizovat
                    chain.doFilter(req, res);
                    return;
                }

                String sub = claims.getSubject();
                UUID userId = UUID.fromString(sub);
                Optional<User> userOpt = userRepo.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    UUID previousTenantId = TenantContext.getTenantId();
                    UUID effectiveTenantId = resolveTenantId(req, claims);

                    var authorities = new HashSet<SimpleGrantedAuthority>();
                    try {
                        TenantContext.setTenantId(effectiveTenantId);
                        var snap = policyService.buildForUser(userId);
                        snap.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                        snap.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
                    } finally {
                        if (previousTenantId == null) {
                            TenantContext.clear();
                        } else {
                            TenantContext.setTenantId(previousTenantId);
                        }
                    }

                    // Aktualizace last seen pro toto sezení
                    sessionService.touch(jti);

                    AbstractAuthenticationToken auth = new JwtPrincipal(sub, user, authorities, claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // neplatný token -> zůstat anonymní; downstream koncové body jsou stejně chráněné
            }
        }

        chain.doFilter(req, res);
    }

    private UUID resolveTenantId(HttpServletRequest req, JWTClaimsSet claims) {
        String tenantHeader = req.getHeader(TenantContextFilter.TENANT_HEADER);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                return UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException ignored) {
                // Náhradní varianta: JWT údaj (claim).
            }
        }

        Object tenantClaim = claims != null ? claims.getClaim("tenantId") : null;
        if (tenantClaim instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
