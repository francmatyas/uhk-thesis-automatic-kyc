package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class JwtPrincipal extends AbstractAuthenticationToken {
    private final String subject; // subject (e.g., user ID UUID string)
    private final Object principal; // can be domain User or any principal object
    private final JWTClaimsSet claims;

    public JwtPrincipal(String subject, Object principal, Collection<? extends GrantedAuthority> authorities, JWTClaimsSet claims) {
        super(authorities);
        this.subject = subject;
        this.principal = principal;
        this.claims = claims;
        setAuthenticated(true);
    }

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
        return subject;
    }
}
