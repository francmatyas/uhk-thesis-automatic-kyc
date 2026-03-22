package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(3)
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapTestTenantRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapTestTenantRunner.class);

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserTenantMembershipRepository userTenantMembershipRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final UserEmailLookupService userEmailLookupService;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.test-tenant.slug:test-tenant}")
    private String testTenantSlug;

    @Value("${app.bootstrap.test-tenant.name:Test Tenant}")
    private String testTenantName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("Bootstrap test tenant skipped: admin email not set.");
            return;
        }

        User admin = userEmailLookupService.findByEmail(adminEmail).orElse(null);
        if (admin == null) {
            log.warn("Bootstrap test tenant skipped: admin user '{}' not found.", adminEmail);
            return;
        }

        Tenant tenant = tenantRepository.findBySlug(testTenantSlug).orElseGet(() -> {
            Tenant t = new Tenant();
            t.setName(testTenantName);
            t.setSlug(testTenantSlug);
            t.setStatus(TenantStatus.ACTIVE);
            t.setOwnerUser(admin);
            return tenantRepository.save(t);
        });

        Role ownerRole = roleRepository.findByNameAndScope("OWNER", RoleScope.TENANT)
                .orElseThrow(() -> new IllegalStateException("TENANT OWNER role not found; run authz bootstrap first."));

        boolean membershipExists = userTenantMembershipRepository
                .findByUserIdAndTenantId(admin.getId(), tenant.getId()).isPresent();
        if (!membershipExists) {
            UserTenantMembership membership = new UserTenantMembership();
            membership.setUser(admin);
            membership.setTenant(tenant);
            membership.setDefault(true);
            userTenantMembershipRepository.save(membership);
        }

        boolean roleAssigned = userTenantRoleRepository.existsByUserIdAndRoleIdAndTenantId(
                admin.getId(), ownerRole.getId(), tenant.getId()
        );
        if (!roleAssigned) {
            UserTenantRole utr = new UserTenantRole();
            utr.setUser(admin);
            utr.setRole(ownerRole);
            utr.setTenant(tenant);
            userTenantRoleRepository.save(utr);
        }

        log.info("Bootstrap test tenant '{}' ready; admin '{}' assigned as OWNER.", testTenantSlug, adminEmail);
    }
}