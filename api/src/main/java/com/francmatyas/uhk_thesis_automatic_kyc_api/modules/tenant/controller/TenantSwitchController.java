package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.AppProps;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.JwtService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.PolicyService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import com.nimbusds.jwt.JWTClaimsSet;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantSwitchController {

    private final TenantAccessService tenantAccessService;
    private final PolicyService policyService;
    private final JwtService jwtService;
    private final AppProps props;
    private final UserSessionService sessionService;
    private final AuditLogService auditLogService;
    private final UserAgentAnalyzer userAgentAnalyzer;

    @GetMapping("/resolve/{slug}")
    public ResponseEntity<?> resolveTenant(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String slug
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (slug == null || slug.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_slug"));
        }

        var tenant = tenantAccessService.findTenantBySlug(slug);
        if (tenant.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "tenant_not_found"));
        }

        if (!tenantAccessService.canAccessTenant(currentUser, tenant.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", "not_member_of_tenant"));
        }

        return ResponseEntity.ok(Map.of(
                "id", tenant.get().getId(),
                "name", tenant.get().getName(),
                "slug", tenant.get().getSlug(),
                "status", tenant.get().getStatus()
        ));
    }

    @PostMapping("/switch")
    public ResponseEntity<?> switchTenant(
            @AuthenticationPrincipal User currentUser,
            @RequestBody SwitchTenantRequest req,
            HttpServletRequest httpReq
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID targetTenantId = null;
        if (req != null && req.tenantId != null && !req.tenantId.isBlank()) {
            try {
                targetTenantId = UUID.fromString(req.tenantId.trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "invalid_tenant_id"));
            }
        } else if (req != null && req.tenantSlug != null && !req.tenantSlug.isBlank()) {
            var tenant = tenantAccessService.findTenantBySlug(req.tenantSlug);
            if (tenant.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "tenant_not_found"));
            }
            targetTenantId = tenant.get().getId();
        }

        if (targetTenantId == null && !currentUser.isProviderUser()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "tenant_required"));
        }

        if (targetTenantId != null && !tenantAccessService.canAccessTenant(currentUser, targetTenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "reason", "not_member_of_tenant"));
        }

        UUID previous = TenantContext.getTenantId();
        try {
            String currentToken = resolveToken(httpReq);
            boolean rememberMe = false;
            String currentJti = null;
            UUID fromTenantId = null;
            Optional<UserSession> currentSession = Optional.empty();
            if (currentToken != null && !currentToken.isBlank()) {
                try {
                    JWTClaimsSet currentClaims = jwtService.parseAndVerify(currentToken);
                    currentJti = JwtService.getJti(currentClaims);
                    fromTenantId = parseTenantIdClaim(currentClaims);
                    if (currentJti != null && !currentJti.isBlank()) {
                        currentSession = sessionService.findByJti(currentJti);
                        rememberMe = currentSession
                                .map(UserSession::isRememberMe)
                                .orElse(false);
                        if (fromTenantId == null) {
                            fromTenantId = currentSession.map(UserSession::getTenantId).orElse(null);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            TenantContext.setTenantId(targetTenantId);
            var snap = policyService.buildForUser(currentUser.getId());

            Map<String, Object> claims = new HashMap<>();
            claims.put("email", currentUser.getEmail());
            if (currentUser.getFullName() != null) {
                claims.put("name", currentUser.getFullName());
            }
            if (targetTenantId != null) {
                claims.put("tenantId", targetTenantId.toString());
            }

            int expMinutes = rememberMe ? props.jwtRememberTtlMinutes() : props.jwtAccessTtlMinutes();
            String jwt = jwtService.createAccessToken(
                    currentUser.getId().toString(),
                    expMinutes,
                    snap.roles(),
                    snap.permissions(),
                    snap.policyVersion(),
                    claims
            );

            JWTClaimsSet parsed = jwtService.parseAndVerify(jwt);
            String jti = JwtService.getJti(parsed);
            var iat = JwtService.getIssuedAt(parsed);
            var exp = JwtService.getExpiresAt(parsed);

            String ip = httpReq.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
            String ua = httpReq.getHeader("User-Agent");

            Map<String, Object> device = new HashMap<>();
            if (ua != null && !ua.isBlank()) {
                UserAgent parsedUa = userAgentAnalyzer.parse(ua);
                device.put("deviceType",     nullIfUnknown(parsedUa.getValue("DeviceClass")));
                device.put("deviceVendor",   nullIfUnknown(parsedUa.getValue("DeviceBrand")));
                device.put("deviceModel",    nullIfUnknown(parsedUa.getValue("DeviceName")));
                device.put("osName",         nullIfUnknown(parsedUa.getValue("OperatingSystemName")));
                device.put("osVersion",      nullIfUnknown(parsedUa.getValue("OperatingSystemVersionMajor")));
                device.put("browserName",    nullIfUnknown(parsedUa.getValue("AgentName")));
                device.put("browserVersion", nullIfUnknown(parsedUa.getValue("AgentVersionMajor")));
                device.put("cpuArch",        nullIfUnknown(parsedUa.getValue("DeviceCpu")));
            }

            UserSession rotatedSession = sessionService.rotateOrCreateForSwitch(
                    currentUser,
                    currentJti,
                    jti,
                    iat,
                    exp,
                    ip,
                    ua,
                    rememberMe,
                    targetTenantId,
                    device
            );
            Map<String, Object> oldValue = new HashMap<>();
            oldValue.put("tenantId", fromTenantId != null ? fromTenantId.toString() : null);

            Map<String, Object> newValue = new HashMap<>();
            newValue.put("tenantId", targetTenantId != null ? targetTenantId.toString() : null);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("switchSource", "TENANT_SWITCH");
            metadata.put("sessionId", rotatedSession.getId() != null ? rotatedSession.getId().toString() : null);
            metadata.put("previousJti", currentJti);
            metadata.put("newJti", jti);

            auditLogService.logUserAction(
                    currentUser.getId(),
                    targetTenantId,
                    "TENANT",
                    targetTenantId != null ? targetTenantId.toString() : "provider",
                    "SWITCH_ACTIVE_TENANT",
                    oldValue,
                    newValue,
                    metadata,
                    ip,
                    ua,
                    parseUuidHeader(httpReq, "X-Correlation-Id"),
                    trimHeader(httpReq, "X-Request-Id")
            );

            ResponseCookie cookie = ResponseCookie.from(props.jwtCookieName(), jwt)
                    .httpOnly(true)
                    .secure(props.isProd())
                    .sameSite(props.jwtSameSite())
                    .path("/")
                    .maxAge(Duration.ofMinutes(expMinutes))
                    .build();

            Map<String, Object> body = new HashMap<>();
            body.put("activeTenantId", targetTenantId);
            if (targetTenantId != null) {
                tenantAccessService.findTenant(targetTenantId).ifPresent(t -> {
                    body.put("tenant", Map.of(
                            "id", t.getId(),
                            "name", t.getName(),
                            "slug", t.getSlug(),
                            "status", t.getStatus()
                    ));
                });
            }
            body.put("roles", snap.roles());
            body.put("permissions", snap.permissions());
            body.put("policyVersion", snap.policyVersion());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(body);
        } finally {
            TenantContext.setTenantId(previous);
        }
    }

    private String resolveToken(HttpServletRequest httpReq) {
        if (httpReq.getCookies() != null) {
            for (var c : httpReq.getCookies()) {
                if (props.jwtCookieName().equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        String auth = httpReq.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        return null;
    }

    private UUID parseTenantIdClaim(JWTClaimsSet claims) {
        Object claim = claims != null ? claims.getClaim("tenantId") : null;
        if (claim instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private UUID parseUuidHeader(HttpServletRequest httpReq, String header) {
        String value = trimHeader(httpReq, header);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String trimHeader(HttpServletRequest httpReq, String header) {
        String value = httpReq.getHeader(header);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullIfUnknown(String value) {
        return (value == null || value.isBlank() || "?? unknown ??".equalsIgnoreCase(value)) ? null : value;
    }

    @Data
    public static class SwitchTenantRequest {
        public String tenantId;
        public String tenantSlug;
    }
}
