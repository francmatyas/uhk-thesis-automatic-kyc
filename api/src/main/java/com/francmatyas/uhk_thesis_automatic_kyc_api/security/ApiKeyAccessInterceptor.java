package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyAccessInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ApiKeyPrincipal)) {
            return true;
        }

        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        boolean apiKeyAccessible = hm.hasMethodAnnotation(ApiKeyAccessible.class)
                || hm.getBeanType().isAnnotationPresent(ApiKeyAccessible.class);
        if (!apiKeyAccessible) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"api_key_endpoint_not_enabled\"}");
            return false;
        }

        for (MethodParameter parameter : hm.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(AuthenticationPrincipal.class)) {
                Class<?> parameterType = parameter.getParameterType();
                if (User.class.isAssignableFrom(parameterType)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"api_key_not_supported_for_endpoint\"}");
                    return false;
                }
                if (!ApiKeyPrincipal.class.isAssignableFrom(parameterType) && !Object.class.equals(parameterType)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"api_key_principal_type_unsupported\"}");
                    return false;
                }
            }
        }

        return true;
    }
}
