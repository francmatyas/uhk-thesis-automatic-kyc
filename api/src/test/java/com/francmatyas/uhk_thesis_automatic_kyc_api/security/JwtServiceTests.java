package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    private static final String SECRET = "unit-test-secret-12345678901234567890";

    private static AppProps propsWithIssuer(String issuer) {
        AppProps props = new AppProps();
        props.getJwt().setSecret(SECRET);
        props.getJwt().setIssuer(issuer);
        props.getJwt().setAccessTtlMinutes(5);
        props.getJwt().setCookieName("AUTH_TOKEN");
        props.getJwt().setSameSite("Lax");
        return props;
    }

    // -------------------------------------------------------------------------
    // Úspěšný scénář
    // -------------------------------------------------------------------------

    @Test
    void createAndVerifyToken() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                "123e4567-e89b-12d3-a456-426614174000",
                5,
                List.of("USER"),
                List.of("read"),
                1,
                Map.of("email", "test@example.com")
        );

        assertNotNull(token);
        var claims = service.parseAndVerify(token);
        assertEquals("automatic-kyc-api", claims.getIssuer());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", claims.getSubject());
    }

    @Test
    void tokenContainsRolesAndPermissionsClaims() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                UUID.randomUUID().toString(),
                5,
                List.of("OWNER", "ADMIN"),
                List.of("provider.users:read", "provider.tenants:read"),
                2,
                Map.of()
        );

        var claims = service.parseAndVerify(token);
        assertEquals(List.of("OWNER", "ADMIN"), claims.getClaim("roles"));
        assertEquals(List.of("provider.users:read", "provider.tenants:read"), claims.getClaim("permissions"));
        assertEquals(2L, ((Number) claims.getClaim("policyVersion")).longValue());
    }

    @Test
    void tokenContainsJwtId() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                UUID.randomUUID().toString(), 5, List.of(), List.of(), 1, Map.of());

        var claims = service.parseAndVerify(token);
        assertNotNull(claims.getJWTID(), "Token must contain a jti (JWT ID)");
        assertFalse(claims.getJWTID().isBlank());
    }

    @Test
    void tokenContainsExtraCustomClaims() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        UUID tenantId = UUID.randomUUID();
        String token = service.createAccessToken(
                UUID.randomUUID().toString(), 5, List.of(), List.of(), 1,
                Map.of("tenantId", tenantId.toString(), "email", "x@example.com"));

        var claims = service.parseAndVerify(token);
        assertEquals(tenantId.toString(), claims.getClaim("tenantId"));
        assertEquals("x@example.com", claims.getClaim("email"));
    }

    @Test
    void getJtiHelperExtractsJwtId() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                UUID.randomUUID().toString(), 5, List.of(), List.of(), 1, Map.of());

        var claims = service.parseAndVerify(token);
        String jti = JwtService.getJti(claims);
        assertNotNull(jti);
        assertFalse(jti.isBlank());
    }

    @Test
    void getIssuedAtAndExpiresAtHelpersReturnInstants() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        Instant before = Instant.now().minusSeconds(2);
        String token = service.createAccessToken(
                UUID.randomUUID().toString(), 5, List.of(), List.of(), 1, Map.of());

        var claims = service.parseAndVerify(token);
        Instant iat = JwtService.getIssuedAt(claims);
        Instant exp = JwtService.getExpiresAt(claims);

        assertNotNull(iat);
        assertNotNull(exp);
        assertTrue(iat.isAfter(before), "issuedAt should be after test start");
        assertTrue(exp.isAfter(iat), "expiresAt should be after issuedAt");
    }

    // -------------------------------------------------------------------------
    // Chybové scénáře
    // -------------------------------------------------------------------------

    @Test
    void rejectsWrongIssuer() {
        JwtService signer = new JwtService(propsWithIssuer("other-issuer"));
        String token = signer.createAccessToken(
                "123e4567-e89b-12d3-a456-426614174000",
                5,
                List.of("USER"),
                List.of("read"),
                1,
                Map.of("email", "test@example.com")
        );

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                "123e4567-e89b-12d3-a456-426614174000",
                -10,
                List.of("USER"),
                List.of("read"),
                1,
                Map.of("email", "test@example.com")
        );

        assertThrows(RuntimeException.class, () -> service.parseAndVerify(token));
    }

    @Test
    void rejectsTamperedSignature() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        String token = service.createAccessToken(
                UUID.randomUUID().toString(), 5, List.of(), List.of(), 1, Map.of());

        // Poškození podpisu: nahrazení posledních znaků
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThrows(RuntimeException.class, () -> service.parseAndVerify(tampered));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() throws Exception {
        // Podepsat jiným secretem, ale se správným issuerem
        String differentSecret = "totally-different-secret-key-xyz-987654321";
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("automatic-kyc-api")
                .subject(UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .issueTime(Date.from(Instant.now()))
                .notBeforeTime(Date.from(Instant.now()))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(differentSecret.getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> service.parseAndVerify("not.a.jwt"));
    }

    @Test
    void rejectsCompletelyGarbageInput() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> service.parseAndVerify("garbage-input-no-dots"));
    }

    @Test
    void rejectsNullToken() {
        JwtService service = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> service.parseAndVerify(null));
    }

    @Test
    void rejectsTokenWithMissingSubject() throws Exception {
        // Vytvoření tokenu bez subject údaje
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("automatic-kyc-api")
                // bez .subject(...)
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .issueTime(Date.from(Instant.now()))
                .notBeforeTime(Date.from(Instant.now()))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }

    @Test
    void rejectsTokenWithMissingExpiration() throws Exception {
        // Vytvoření tokenu bez expiration údaje
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("automatic-kyc-api")
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .jwtID(UUID.randomUUID().toString())
                // bez .expirationTime(...)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }

    @Test
    void rejectsNotYetValidToken() throws Exception {
        // Vytvoření tokenu s nbf daleko v budoucnosti
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("automatic-kyc-api")
                .subject(UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .issueTime(Date.from(Instant.now()))
                .notBeforeTime(Date.from(Instant.now().plusSeconds(3600))) // 1 hour in future
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }

    @Test
    void rejectsTokenWithFutureIssuedAt() throws Exception {
        // iat daleko v budoucnosti — indikuje clock skew útok
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("automatic-kyc-api")
                .subject(UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(3700)))
                .issueTime(Date.from(Instant.now().plusSeconds(3600))) // 1 hour in future
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        JwtService verifier = new JwtService(propsWithIssuer("automatic-kyc-api"));
        assertThrows(RuntimeException.class, () -> verifier.parseAndVerify(token));
    }
}
