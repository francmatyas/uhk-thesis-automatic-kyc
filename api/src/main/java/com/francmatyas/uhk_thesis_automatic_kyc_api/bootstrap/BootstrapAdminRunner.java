package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserPreferences;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEmailLookupService userEmailLookupService;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-given-name:Root}")
    private String adminGivenName;

    @Value("${app.bootstrap.admin-family-name:User}")
    private String adminFamilyName;

    @Value("${app.bootstrap.admin-role-name:OWNER}")
    private String adminRoleName;

    @Value("${app.bootstrap.admin-role-scope:PROVIDER}")
    private String adminRoleScope;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("Bootstrap admin disabled: admin email/password not set.");
            return;
        }

        RoleScope scope;
        try {
            scope = RoleScope.valueOf(adminRoleScope.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bootstrap admin skipped: invalid role scope '{}'.", adminRoleScope);
            return;
        }

        if (scope != RoleScope.PROVIDER) {
            log.warn("Bootstrap admin skipped: only PROVIDER scope is supported without a tenant.");
            return;
        }

        Role role = roleRepository.findByNameAndScope(adminRoleName, scope)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(adminRoleName);
                    r.setScope(scope);
                    r.setDescription("Bootstrap owner role");
                    r.setPriority(1000);
                    return roleRepository.save(r);
                });

        var existingUser = userEmailLookupService.findByEmail(adminEmail).orElse(null);
        if (existingUser != null) {
            ensureProviderAssignment(existingUser, role);
            log.info("Bootstrap admin user already exists; role assignment ensured.");
            return;
        }

        User user = new User();
        user.setEmail(adminEmail.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(sha256Hex(adminPassword)));
        user.setGivenName(adminGivenName);
        user.setFamilyName(adminFamilyName);
        user.setEnabled(true);
        user.setProviderUser(scope == RoleScope.PROVIDER);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        user.setProfile(profile);

        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        user.setPreferences(prefs);

        userRepository.save(user);

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(user);
        utr.setRole(role);
        utr.setTenant(null);
        userTenantRoleRepository.save(utr);

        log.info("Bootstrap admin created: {}", adminEmail);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void ensureProviderAssignment(User user, Role role) {
        if (!user.isProviderUser()) {
            user.setProviderUser(true);
            userRepository.save(user);
        }

        boolean assigned = userTenantRoleRepository.existsByUserIdAndRoleIdAndTenantId(
                user.getId(), role.getId(), null
        );
        if (!assigned) {
            UserTenantRole utr = new UserTenantRole();
            utr.setUser(user);
            utr.setRole(role);
            utr.setTenant(null);
            userTenantRoleRepository.save(utr);
        }
    }
}
