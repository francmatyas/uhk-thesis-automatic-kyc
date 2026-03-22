package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Permission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RolePermission;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.PermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RolePermissionRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTests {

    @Test
    void setRolePermissionsReplacesRolePermissionsTableRows() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        EntityManager entityManager = mock(EntityManager.class);

        RoleService service = new RoleService(
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                entityManager
        );

        UUID roleId = UUID.randomUUID();
        UUID permA = UUID.randomUUID();
        UUID permB = UUID.randomUUID();

        Role existingRole = new Role();
        existingRole.setId(roleId);
        existingRole.setName("Manager");
        existingRole.setScope(RoleScope.TENANT);
        existingRole.setRolePermissions(new LinkedHashSet<>());

        Permission permissionA = new Permission();
        permissionA.setId(permA);
        Permission permissionB = new Permission();
        permissionB.setId(permB);

        Role reloadedRole = new Role();
        reloadedRole.setId(roleId);
        reloadedRole.setName("Manager");
        reloadedRole.setScope(RoleScope.TENANT);
        reloadedRole.setRolePermissions(new LinkedHashSet<>());
        RolePermission rpA = new RolePermission();
        rpA.setId(UUID.randomUUID());
        rpA.setRole(reloadedRole);
        rpA.setPermission(permissionA);
        RolePermission rpB = new RolePermission();
        rpB.setId(UUID.randomUUID());
        rpB.setRole(reloadedRole);
        rpB.setPermission(permissionB);
        reloadedRole.getRolePermissions().add(rpA);
        reloadedRole.getRolePermissions().add(rpB);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole), Optional.of(reloadedRole));
        when(permissionRepository.findAllById(any(Iterable.class))).thenReturn(List.of(permissionA, permissionB));
        when(rolePermissionRepository.saveAll(any(Iterable.class))).thenAnswer(invocation -> {
            List<RolePermission> saved = new ArrayList<>();
            invocation.<Iterable<RolePermission>>getArgument(0).forEach(saved::add);
            return saved;
        });

        var result = service.setRolePermissions(
                roleId.toString(),
                List.of(permA.toString(), permA.toString(), permB.toString())
        );

        verify(rolePermissionRepository).deleteAllByRoleId(roleId);
        verify(entityManager).flush();
        verify(entityManager).clear();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RolePermission>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).saveAll(saveCaptor.capture());

        List<RolePermission> savedRows = new ArrayList<>();
        saveCaptor.getValue().forEach(savedRows::add);
        assertEquals(2, savedRows.size());
        assertNotNull(savedRows.get(0).getRole());
        assertEquals(roleId, savedRows.get(0).getRole().getId());

        assertEquals(2, result.getPermissionIds().size());
        org.junit.jupiter.api.Assertions.assertTrue(result.getPermissionIds().contains(permA.toString()));
        org.junit.jupiter.api.Assertions.assertTrue(result.getPermissionIds().contains(permB.toString()));
    }
}
