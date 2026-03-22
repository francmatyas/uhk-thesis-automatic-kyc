package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Map;

@Component
public class ApiAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public ApiAwareAuthenticationEntryPoint(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        boolean handlerExists = hasHandlerFor(request);

        if (!handlerExists) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_found\"}");
            return;
        }

        // handler existuje, ale volající není autentizovaný
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }

    private boolean hasHandlerFor(HttpServletRequest request) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            // kontrola cesty + HTTP metody + parametrů atd.
            if (info.getMatchingCondition(request) != null) {
                return true;
            }
        }
        return false;
    }
}
