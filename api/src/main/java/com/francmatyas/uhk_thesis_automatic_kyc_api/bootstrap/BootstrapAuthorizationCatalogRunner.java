package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Permission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RolePermission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.PermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RolePermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Naplní systémový autorizační katalog (oprávnění, role a přiřazení).
 * Níže lze doplnit nebo upravit seed záznamy podle požadovaného RBAC modelu.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.bootstrap.authz", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BootstrapAuthorizationCatalogRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAuthorizationCatalogRunner.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedRoles();
        seedRoleGrants();
        log.info("Authorization catalog bootstrap completed.");
    }

    private void seedPermissions() {
        for (PermissionSeed seed : permissionSeeds()) {
            permissionRepository.findByResourceAndAction(seed.resource(), seed.action())
                    .orElseGet(() -> {
                        Permission permission = new Permission();
                        permission.setResource(seed.resource());
                        permission.setAction(seed.action());
                        permission.setDescription(seed.description());
                        return permissionRepository.save(permission);
                    });
        }
    }

    private void seedRoles() {
        for (RoleSeed seed : roleSeeds()) {
            Role role = roleRepository.findByNameAndScope(seed.name(), seed.scope())
                    .orElseGet(() -> {
                        Role created = new Role();
                        created.setName(seed.name());
                        created.setScope(seed.scope());
                        return created;
                    });

            role.setDescription(seed.description());
            role.setPriority(seed.priority());
            roleRepository.save(role);
        }
    }

    private void seedRoleGrants() {
        for (RoleGrantSeed grant : roleGrantSeeds()) {
            Role role = roleRepository.findByNameAndScope(grant.roleName(), grant.scope())
                    .orElseThrow();

            for (String permissionKey : grant.permissionKeys()) {
                String[] parts = permissionKey.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }

                Permission permission = permissionRepository.findByResourceAndAction(parts[0], parts[1])
                        .orElseThrow();

                boolean exists = rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId());
                if (exists) {
                    continue;
                }

                RolePermission rp = new RolePermission();
                rp.setRole(role);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
            }
        }
    }

    private List<PermissionSeed> permissionSeeds() {
        return List.of(
                // Oblast poskytovatele
                new PermissionSeed("provider.tenants", "read", "View tenants"),
                new PermissionSeed("provider.tenants", "create", "Create tenants"),
                new PermissionSeed("provider.tenants", "update", "Update tenants"),
                new PermissionSeed("provider.tenants", "delete", "Delete tenants"),
                new PermissionSeed("provider.users", "read", "View users"),
                new PermissionSeed("provider.users", "create", "Create users"),
                new PermissionSeed("provider.users", "update", "Update users"),
                new PermissionSeed("provider.users", "delete", "Delete users"),
                new PermissionSeed("provider.roles", "read", "View roles"),
                new PermissionSeed("provider.roles", "create", "Create roles"),
                new PermissionSeed("provider.roles", "update", "Update roles"),
                new PermissionSeed("provider.roles", "delete", "Delete roles"),
                new PermissionSeed("provider.permissions", "read", "View permissions"),
                new PermissionSeed("provider.permissions", "create", "Create permissions"),
                new PermissionSeed("provider.permissions", "update", "Update permissions"),
                new PermissionSeed("provider.permissions", "delete", "Delete permissions"),
                new PermissionSeed("provider.audit-logs", "read", "View platform audit logs"),
                // Oblast tenanta
                new PermissionSeed("tenant.tenants", "read", "View active tenant details"),
                new PermissionSeed("tenant.tenants", "update", "Update active tenant details"),
                new PermissionSeed("tenant.members", "read", "View tenant members"),
                new PermissionSeed("tenant.members", "create", "Add tenant members"),
                new PermissionSeed("tenant.members", "update", "Update tenant members"),
                new PermissionSeed("tenant.members", "delete", "Remove tenant members"),
                new PermissionSeed("tenant.roles", "read", "View tenant roles"),
                new PermissionSeed("tenant.roles", "update", "Manage tenant role assignment"),
                new PermissionSeed("tenant.api-keys", "read", "View tenant API keys"),
                new PermissionSeed("tenant.api-keys", "create", "Create tenant API keys"),
                new PermissionSeed("tenant.api-keys", "update", "Update tenant API keys"),
                new PermissionSeed("tenant.api-keys", "delete", "Delete tenant API keys"),
                new PermissionSeed("tenant.webhooks", "read", "View tenant webhooks"),
                new PermissionSeed("tenant.webhooks", "create", "Create tenant webhooks"),
                new PermissionSeed("tenant.webhooks", "update", "Update tenant webhooks"),
                new PermissionSeed("tenant.webhooks", "delete", "Delete tenant webhooks"),
                new PermissionSeed("tenant.audit-logs", "read", "View tenant audit logs"),
                // KYC oblast poskytovatele
                new PermissionSeed("provider.verifications", "read", "View KYC verifications across tenants"),
                new PermissionSeed("provider.journey-templates", "read", "View KYC journey templates across tenants"),
                new PermissionSeed("provider.journey-templates", "create", "Create KYC journey templates across tenants"),
                new PermissionSeed("provider.journey-templates", "update", "Update KYC journey templates across tenants"),
                new PermissionSeed("provider.journey-templates", "delete", "Delete KYC journey templates across tenants"),
                // KYC oblast tenanta
                new PermissionSeed("tenant.journey-templates", "read", "View tenant KYC journey templates"),
                new PermissionSeed("tenant.journey-templates", "create", "Create tenant KYC journey templates"),
                new PermissionSeed("tenant.journey-templates", "update", "Update tenant KYC journey templates"),
                new PermissionSeed("tenant.journey-templates", "delete", "Delete tenant KYC journey templates"),
                new PermissionSeed("tenant.client-identities", "read", "View tenant KYC client identities"),
                new PermissionSeed("tenant.verifications", "read", "View tenant KYC verifications")
        );
    }

    private List<RoleSeed> roleSeeds() {
        return List.of(
                new RoleSeed("OWNER", RoleScope.PROVIDER, "Provider owner", 1200),
                new RoleSeed("ADMIN", RoleScope.PROVIDER, "Provider administrator", 1000),
                new RoleSeed("SUPPORT", RoleScope.PROVIDER, "Provider support", 500),
                new RoleSeed("OWNER", RoleScope.TENANT, "Tenant owner", 1000),
                new RoleSeed("ADMIN", RoleScope.TENANT, "Tenant administrator", 900),
                new RoleSeed("OPERATOR", RoleScope.TENANT, "Tenant operator", 500)
        );
    }

    private List<RoleGrantSeed> roleGrantSeeds() {
        return List.of(
                // Přiřazení oprávnění pro oblast poskytovatele
                new RoleGrantSeed("OWNER", RoleScope.PROVIDER, List.of(
                        "provider.tenants:read", "provider.tenants:create", "provider.tenants:update", "provider.tenants:delete",
                        "provider.users:read", "provider.users:create", "provider.users:update", "provider.users:delete",
                        "provider.roles:read", "provider.roles:create", "provider.roles:update", "provider.roles:delete",
                        "provider.permissions:read", "provider.permissions:create", "provider.permissions:update", "provider.permissions:delete",
                        "provider.audit-logs:read",
                        "provider.verifications:read",
                        "provider.journey-templates:read", "provider.journey-templates:create", "provider.journey-templates:update", "provider.journey-templates:delete"
                )),
                new RoleGrantSeed("ADMIN", RoleScope.PROVIDER, List.of(
                        "provider.tenants:read", "provider.tenants:create", "provider.tenants:update", "provider.tenants:delete",
                        "provider.users:read", "provider.users:create", "provider.users:update", "provider.users:delete",
                        "provider.roles:read", "provider.roles:create", "provider.roles:update", "provider.roles:delete",
                        "provider.permissions:read", "provider.permissions:create", "provider.permissions:update", "provider.permissions:delete",
                        "provider.audit-logs:read",
                        "provider.verifications:read",
                        "provider.journey-templates:read", "provider.journey-templates:create", "provider.journey-templates:update", "provider.journey-templates:delete"
                )),
                new RoleGrantSeed("SUPPORT", RoleScope.PROVIDER, List.of(
                        "provider.tenants:read",
                        "provider.users:read",
                        "provider.audit-logs:read",
                        "provider.verifications:read",
                        "provider.journey-templates:read"
                )),
                // Přiřazení oprávnění pro oblast tenanta
                new RoleGrantSeed("OWNER", RoleScope.TENANT, List.of(
                        "tenant.tenants:read", "tenant.tenants:update",
                        "tenant.members:read", "tenant.members:create", "tenant.members:update", "tenant.members:delete",
                        "tenant.roles:read", "tenant.roles:update",
                        "tenant.api-keys:read", "tenant.api-keys:create", "tenant.api-keys:update", "tenant.api-keys:delete",
                        "tenant.webhooks:read", "tenant.webhooks:create", "tenant.webhooks:update", "tenant.webhooks:delete",
                        "tenant.audit-logs:read",
                        "tenant.journey-templates:read", "tenant.journey-templates:create", "tenant.journey-templates:update", "tenant.journey-templates:delete",
                        "tenant.client-identities:read",
                        "tenant.verifications:read"
                )),
                new RoleGrantSeed("ADMIN", RoleScope.TENANT, List.of(
                        "tenant.tenants:read", "tenant.tenants:update",
                        "tenant.members:read", "tenant.members:create", "tenant.members:update", "tenant.members:delete",
                        "tenant.roles:read",
                        "tenant.journey-templates:read", "tenant.journey-templates:create", "tenant.journey-templates:update", "tenant.journey-templates:delete",
                        "tenant.client-identities:read",
                        "tenant.verifications:read"
                )),
                new RoleGrantSeed("OPERATOR", RoleScope.TENANT, List.of(
                        "tenant.tenants:read",
                        "tenant.members:read",
                        "tenant.journey-templates:read",
                        "tenant.client-identities:read",
                        "tenant.verifications:read"
                ))
        );
    }

    private record PermissionSeed(String resource, String action, String description) {
    }

    private record RoleSeed(String name, RoleScope scope, String description, int priority) {
    }

    private record RoleGrantSeed(String roleName, RoleScope scope, List<String> permissionKeys) {
    }
}
