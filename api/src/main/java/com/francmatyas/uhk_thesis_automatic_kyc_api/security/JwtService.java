package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final int MIN_SECRET_BYTES = 32;

    private final AppProps props;

    @PostConstruct
    void validateSecret() {
        String secret = props.jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_KEY is not configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        String issuer = props.jwtIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER is not configured");
        }
    }

    public String createAccessToken(String subject, int expMinutes, List<String> roles, List<String> permissions, int policyVersion, Map<String, Object> extra) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expMinutes * 60L);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .issuer(props.jwtIssuer())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .subject(subject)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("policyVersion", policyVersion);

        if (extra != null) extra.forEach(b::claim);

        try {
            String secret = props.jwtSecret();
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("JWT secret is not configured");
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), b.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    public JWTClaimsSet parseAndVerify(String token) {
        try {
            String secret = props.jwtSecret();
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("JWT secret is not configured");
            }
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)))) {
                throw new RuntimeException("Invalid signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            validateClaims(claims);
            return claims;
        } catch (Exception e) {
            throw new RuntimeException("JWT parse/verify failed", e);
        }
    }

    private void validateClaims(JWTClaimsSet claims) {
        Instant now = Instant.now();

        String expectedIssuer = props.jwtIssuer();
        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid issuer");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Missing subject");
        }

        Date exp = claims.getExpirationTime();
        if (exp == null) {
            throw new IllegalArgumentException("Missing expiration");
        }
        if (exp.toInstant().isBefore(now.minusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new IllegalArgumentException("Token expired");
        }

        Date nbf = claims.getNotBeforeTime();
        if (nbf != null && nbf.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new IllegalArgumentException("Token not yet valid");
        }

        Date iat = claims.getIssueTime();
        if (iat != null && iat.toInstant().isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new IllegalArgumentException("Issued-at is in the future");
        }
    }

    // Pomocné metody pro získání běžných polí
    public static String getJti(JWTClaimsSet claims) {
        return claims.getJWTID();
    }

    public static Instant getIssuedAt(JWTClaimsSet claims) {
        Date iat = claims.getIssueTime();
        return iat != null ? iat.toInstant() : null;
    }

    public static Instant getExpiresAt(JWTClaimsSet claims) {
        Date exp = claims.getExpirationTime();
        return exp != null ? exp.toInstant() : null;
    }
}
