package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProps {
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private String env;

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer;
        private int accessTtlMinutes;
        private int rememberTtlMinutes;
        private String cookieName;
        private String sameSite;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins;
    }

    public String jwtSecret() {
        return jwt.getSecret();
    }

    public String jwtIssuer() {
        return jwt.getIssuer();
    }

    public int jwtAccessTtlMinutes() {
        return jwt.getAccessTtlMinutes();
    }

    public int jwtRememberTtlMinutes() {
        return jwt.getRememberTtlMinutes();
    }

    public String jwtCookieName() {
        return jwt.getCookieName();
    }

    public String jwtSameSite() {
        return jwt.getSameSite();
    }

    public List<String> corsAllowedOrigins() {
        return cors.getAllowedOrigins();
    }

    public String env() {
        return env;
    }

    public boolean isProd() {
        return env != null && env.equalsIgnoreCase("production");
    }
}
