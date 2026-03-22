package com.francmatyas.uhk_thesis_automatic_kyc_api.config;

import com.francmatyas.uhk_thesis_automatic_kyc_api.security.ApiKeyAccessInterceptor;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAccessInterceptor apiKeyAccessInterceptor;
    private final RequireActiveTenantInterceptor requireActiveTenantInterceptor;

    public WebMvcConfig(ApiKeyAccessInterceptor apiKeyAccessInterceptor,
                        RequireActiveTenantInterceptor requireActiveTenantInterceptor) {
        this.apiKeyAccessInterceptor = apiKeyAccessInterceptor;
        this.requireActiveTenantInterceptor = requireActiveTenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAccessInterceptor);
        registry.addInterceptor(requireActiveTenantInterceptor);
    }
}
