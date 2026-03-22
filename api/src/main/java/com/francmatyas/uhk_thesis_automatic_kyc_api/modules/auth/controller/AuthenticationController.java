package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogCommand;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserSessionService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserPreferences;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserProfile;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.*;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.FieldCrypto;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationManager authManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;
    private final UserEmailLookupService userEmailLookupService;
    private final JwtService jwtService;
    private final PolicyService policyService;
    private final AppProps props;
    private final UserSessionService sessionService;
    private final UserTenantMembershipRepository membershipRepo;
    private final TenantAccessService tenantAccessService;
    private final UserAgentAnalyzer userAgentAnalyzer;
    private final AuditLogService auditLogService;

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String ip = httpReq.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        String ua = httpReq.getHeader("User-Agent");

        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email, req.password));
        } catch (AuthenticationException e) {
            try {
                String emailHash = FieldCrypto.hashEmail(FieldCrypto.normalizeEmail(req.email));
                auditLogService.log(new AuditLogCommand(
                        null, null, AuditActorType.SYSTEM, null,
                        "USER", emailHash != null ? emailHash : "unknown",
                        "LOGIN", null, null, null,
                        ip, ua, null, null,
                        AuditResult.FAILURE, "invalid_credentials"));
            } catch (Exception ignored) {}
            return ResponseEntity.status(401).body(Map.of("error", "ERROR_INVALID_CREDENTIALS"));
        }

        User user = userEmailLookupService.findByEmail(req.email).orElseThrow();

        // Výběr aktivního tenanta: prioritu má hlavička, pak výchozí tenantId uživatele
        UUID tenantId = null;
        String tenantHeader = httpReq.getHeader("X-Tenant-Id");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                tenantId = UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (tenantId != null && !tenantAccessService.canAccessTenant(user, tenantId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "forbidden", "reason", "not_member_of_tenant"));
        }

        TenantContext.setTenantId(tenantId);
        var snap = policyService.buildForUser(user.getId());
        TenantContext.clear();

        // Bezpečné sestavení vlastních JWT claimů (aby v Map.of nebyly null hodnoty)
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        if (user.getFullName() != null) {
            claims.put("name", user.getFullName());
        }
        if (tenantId != null) {
            claims.put("tenantId", tenantId.toString());
        }

        int expMinutes = req.rememberMe ? props.jwtRememberTtlMinutes() : props.jwtAccessTtlMinutes();

        String jwt = jwtService.createAccessToken(
                user.getId().toString(),
                expMinutes,
                snap.roles(),
                snap.permissions(),
                snap.policyVersion(),
                claims
        );

        ResponseCookie cookie = ResponseCookie.from(props.jwtCookieName(), jwt)
                .httpOnly(true)
                .secure(props.isProd())
                .sameSite(props.jwtSameSite())
                .path("/")
                .maxAge(Duration.ofMinutes(expMinutes))
                .build();

        // Uložení sezení včetně informací o zařízení (z parseru klientského UA)
        var parsed = jwtService.parseAndVerify(jwt);
        String jti = JwtService.getJti(parsed);
        var iat = JwtService.getIssuedAt(parsed);
        var exp = JwtService.getExpiresAt(parsed);

        Map<String, Object> device = new HashMap<>();
        if (ua != null && !ua.isBlank()) {
            UserAgent parsedUa = userAgentAnalyzer.parse(ua);
            device.put("deviceType",    nullIfUnknown(parsedUa.getValue("DeviceClass")));
            device.put("deviceVendor",  nullIfUnknown(parsedUa.getValue("DeviceBrand")));
            device.put("deviceModel",   nullIfUnknown(parsedUa.getValue("DeviceName")));
            device.put("osName",        nullIfUnknown(parsedUa.getValue("OperatingSystemName")));
            device.put("osVersion",     nullIfUnknown(parsedUa.getValue("OperatingSystemVersionMajor")));
            device.put("browserName",   nullIfUnknown(parsedUa.getValue("AgentName")));
            device.put("browserVersion",nullIfUnknown(parsedUa.getValue("AgentVersionMajor")));
            device.put("cpuArch",       nullIfUnknown(parsedUa.getValue("DeviceCpu")));
        }
        var session = sessionService.create(user, jti, iat, exp, ip, ua, req.rememberMe, tenantId, device);

        try {
            Map<String, Object> loginMeta = new HashMap<>();
            loginMeta.put("sessionId", session.getId().toString());
            loginMeta.put("rememberMe", req.rememberMe);
            if (tenantId != null) loginMeta.put("tenantId", tenantId.toString());
            auditLogService.logUserAction(
                    user.getId(), tenantId,
                    "USER", user.getId().toString(),
                    "LOGIN", null, null,
                    loginMeta, ip, ua, null, null);
        } catch (Exception ignored) {}

        // Sestavení těla odpovědi přes HashMapy kvůli povoleným null hodnotám
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("fullName", user.getFullName());
        userMap.put("givenName", user.getGivenName());
        userMap.put("middleName", user.getMiddleName());
        userMap.put("familyName", user.getFamilyName());
        userMap.put("isProviderUser", user.isProviderUser());

        var tenants = membershipRepo.findAllByUserId(user.getId()).stream().map(m -> {
            Map<String, Object> t = new HashMap<>();
            t.put("id", m.getTenant().getId());
            t.put("name", m.getTenant().getName());
            t.put("slug", m.getTenant().getSlug());
            t.put("status", m.getTenant().getStatus());
            t.put("isDefault", m.isDefault());
            return t;
        }).collect(Collectors.toList());

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("id", session.getId());
        sessionMap.put("jti", session.getJti());
        sessionMap.put("issuedAt", session.getIssuedAt());
        sessionMap.put("expiresAt", session.getExpiresAt());
        sessionMap.put("lastSeenAt", session.getLastSeenAt());
        sessionMap.put("ipAddress", session.getIpAddress());
        sessionMap.put("deviceType", session.getDeviceType());
        sessionMap.put("deviceVendor", session.getDeviceVendor());
        sessionMap.put("deviceModel", session.getDeviceModel());
        sessionMap.put("osName", session.getOsName());
        sessionMap.put("osVersion", session.getOsVersion());
        sessionMap.put("browserName", session.getBrowserName());
        sessionMap.put("browserVersion", session.getBrowserVersion());
        sessionMap.put("cpuArch", session.getCpuArch());

        Map<String, Object> body = new HashMap<>();
        body.put("user", userMap);
        body.put("roles", snap.roles());
        body.put("permissions", snap.permissions());
        body.put("policyVersion", snap.policyVersion());
        body.put("session", sessionMap);
        body.put("tenants", tenants);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpReq) {
        // Pokus o revokaci aktuálního sezení, pokud je přítomná cookie
        String token = null;
        if (httpReq.getCookies() != null) {
            for (var c : httpReq.getCookies()) {
                if (props.jwtCookieName().equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        if (token != null) {
            try {
                var claims = jwtService.parseAndVerify(token);
                String jti = JwtService.getJti(claims);
                String sub = claims.getSubject();
                if (jti != null && !jti.isBlank() && sub != null && !sub.isBlank()) {
                    UUID actorId = UUID.fromString(sub);
                    sessionService.revokeByJtiForUser(actorId, jti, "USER_LOGGOUT");
                    try {
                        String logoutIp = httpReq.getHeader("CF-Connecting-IP");
                        if (logoutIp == null || logoutIp.isBlank()) logoutIp = httpReq.getRemoteAddr();
                        auditLogService.logUserAction(
                                actorId, null,
                                "USER_SESSION", jti,
                                "LOGOUT", null, null, null,
                                logoutIp, httpReq.getHeader("User-Agent"), null, null);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
            }
        }
        ResponseCookie cookie = ResponseCookie.from(props.jwtCookieName(), "")
                .httpOnly(true).secure(props.isProd()).sameSite(props.jwtSameSite())
                .path("/").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @PostMapping("/register") // optional (remove in prod if not needed)
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest httpReq) {
        if (userEmailLookupService.findByEmail(req.email).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "ERROR_EMAIL_EXISTS"));
        }
        User u = new User();
        u.setEmail(req.email);
        u.setPassword(passwordEncoder.encode(req.password));
        u.setGivenName(req.givenName);
        u.setFamilyName(req.familyName);
        u.setEnabled(true);

        UserProfile profile = new UserProfile();
        profile.setUser(u);
        u.setProfile(profile);

        UserPreferences prefs = new UserPreferences();
        prefs.setUser(u);
        u.setPreferences(prefs);

        userRepo.save(u);

        try {
            String regIp = httpReq.getHeader("CF-Connecting-IP");
            if (regIp == null || regIp.isBlank()) regIp = httpReq.getRemoteAddr();
            auditLogService.logUserAction(
                    u.getId(), null,
                    "USER", u.getId().toString(),
                    "REGISTER", null, null, null,
                    regIp, httpReq.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Data
    public static class LoginRequest {
        @Email
        public String email;
        @NotBlank
        public String password;
        public boolean rememberMe;
    }

    @Data
    public static class RegisterRequest {
        @Email
        public String email;
        @NotBlank
        public String password;
        public String givenName;
        public String familyName;
    }

    private static String nullIfUnknown(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
