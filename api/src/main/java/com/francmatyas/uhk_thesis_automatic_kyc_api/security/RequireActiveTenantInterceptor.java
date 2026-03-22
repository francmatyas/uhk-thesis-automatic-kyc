package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Vynucuje {@link RequireActiveTenant} na úrovni controlleru/metody.
 *
 * Chování:
 * - Pokud handler není anotovaný: povolit.
 * - Pokud anotovaný je a TenantContext nemá tenantId:
 *   - provider uživatelé jsou povoleni
 *   - non-provider uživatelé dostanou 400
 */
@Component
public class RequireActiveTenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        boolean required = hm.hasMethodAnnotation(RequireActiveTenant.class)
                || hm.getBeanType().isAnnotationPresent(RequireActiveTenant.class);

        if (!required) {
            return true;
        }

        // Kontrakt anotace: provider uživatelé mohou pokračovat i bez tenanta.
        if (TenantContext.getTenantId() == null) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user && user.isProviderUser()) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"tenant_required\"}");
            return false;
        }

        return true;
    }
}
