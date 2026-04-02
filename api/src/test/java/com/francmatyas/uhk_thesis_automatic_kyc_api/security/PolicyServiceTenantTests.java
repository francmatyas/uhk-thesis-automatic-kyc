package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyServiceTenantTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void providerUserWithoutActiveTenantGetsProviderRolesOnly() {
        UUID userId = UUID.randomUUID();

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));

        UserTenantRoleRepository utrRepo = mock(UserTenantRoleRepository.class);

        UserTenantRole providerAssignment = new UserTenantRole();
        providerAssignment.setUser(u);
        providerAssignment.setTenant(null);
        Role providerRole = new Role();
        providerRole.setName("PROVIDER_ADMIN");
        providerRole.setScope(RoleScope.PROVIDER);
        providerAssignment.setRole(providerRole);

        when(utrRepo.findAllByUserIdAndTenantId(userId, null)).thenReturn(List.of(providerAssignment));

        PolicyService svc = new PolicyService(userRepo, utrRepo);
        var snap = svc.buildForUser(userId);

        assertEquals(List.of("PROVIDER_ADMIN"), snap.roles());

        verify(utrRepo, times(1)).findAllByUserIdAndTenantId(userId, null);
        verify(utrRepo, never()).findAllByUserIdAndTenantId(eq(userId), notNull());
    }

    @Test
    void providerUserWithActiveTenantGetsTenantRolesOnly() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        User u = new User();
        u.setId(userId);
        u.setProviderUser(true);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));

        UserTenantRoleRepository utrRepo = mock(UserTenantRoleRepository.class);

        Tenant t = new Tenant();
        t.setId(tenantId);

        UserTenantRole tenantAssignment = new UserTenantRole();
        tenantAssignment.setUser(u);
        tenantAssignment.setTenant(t);
        Role tenantRole = new Role();
        tenantRole.setName("TENANT_OPERATOR");
        tenantRole.setScope(RoleScope.TENANT);
        tenantAssignment.setRole(tenantRole);

        when(utrRepo.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(tenantAssignment));

        PolicyService svc = new PolicyService(userRepo, utrRepo);
        var snap = svc.buildForUser(userId);

        // Přepnutý do tenant kontextu — pouze tenant role, žádné provider role.
        assertEquals(List.of("TENANT_OPERATOR"), snap.roles());

        verify(utrRepo, never()).findAllByUserIdAndTenantId(userId, null);
        verify(utrRepo, times(1)).findAllByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    void tenantUserRequiresActiveTenantToResolveRoles() {
        UUID userId = UUID.randomUUID();

        User u = new User();
        u.setId(userId);
        u.setProviderUser(false);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));

        UserTenantRoleRepository utrRepo = mock(UserTenantRoleRepository.class);

        PolicyService svc = new PolicyService(userRepo, utrRepo);
        var snap = svc.buildForUser(userId);

        assertTrue(snap.roles().isEmpty());
        verifyNoInteractions(utrRepo);
    }
}
