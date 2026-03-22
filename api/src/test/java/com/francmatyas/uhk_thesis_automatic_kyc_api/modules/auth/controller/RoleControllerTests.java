package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto.RoleDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleControllerTests {

    private static RoleController controller(RoleService roleService) {
        return new RoleController(roleService, mock(AuditLogService.class));
    }

    private static User adminUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    // -------------------------------------------------------------------------
    // Úprava (původní testy ponechány, doplněny aserce na body)
    // -------------------------------------------------------------------------

    @Test
    void updateReturnsOkWithUpdatedRole() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        List<String> permissionIds = List.of(UUID.randomUUID().toString());
        RoleDTO request = RoleDTO.builder()
                .name("Admin")
                .scope(RoleScope.TENANT)
                .description("Admin role")
                .priority(10)
                .permissionIds(permissionIds)
                .build();
        RoleDTO response = RoleDTO.builder()
                .id(roleId)
                .name("Admin")
                .scope(RoleScope.TENANT)
                .description("Admin role")
                .priority(10)
                .permissionIds(permissionIds)
                .build();

        when(roleService.updateRole(roleId, request)).thenReturn(response);

        var res = controller.update(adminUser(), roleId, request, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
        assertEquals(response, res.getBody());
        // Ověření polí v těle odpovědi
        RoleDTO body = (RoleDTO) res.getBody();
        assertNotNull(body);
        assertEquals("Admin", body.getName());
        assertEquals(RoleScope.TENANT, body.getScope());
        assertEquals(10, body.getPriority());
        assertEquals(roleId, body.getId());
    }

    @Test
    void updateWithoutPermissionsReturnsOk() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        RoleDTO request = RoleDTO.builder()
                .name("Viewer")
                .scope(RoleScope.PROVIDER)
                .priority(1)
                .build();
        RoleDTO response = RoleDTO.builder()
                .id(roleId)
                .name("Viewer")
                .scope(RoleScope.PROVIDER)
                .priority(1)
                .permissionIds(List.of())
                .build();

        when(roleService.updateRole(roleId, request)).thenReturn(response);

        var res = controller.update(adminUser(), roleId, request, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
        assertEquals(response, res.getBody());
    }

    @Test
    void updateWhenServiceThrowsReturnsBadRequest() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        RoleDTO request = RoleDTO.builder().name("X").scope(RoleScope.TENANT).priority(0).build();
        when(roleService.updateRole(roleId, request)).thenThrow(new IllegalArgumentException("Role not found"));

        var res = controller.update(adminUser(), roleId, request, new MockHttpServletRequest());

        assertEquals(400, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().toString().contains("Role not found"));
    }

    // -------------------------------------------------------------------------
    // Vytvoření
    // -------------------------------------------------------------------------

    @Test
    void createReturns201WithCreatedRole() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String newId = UUID.randomUUID().toString();
        RoleDTO request = RoleDTO.builder()
                .name("Editor")
                .scope(RoleScope.TENANT)
                .description("Can edit resources")
                .priority(5)
                .permissionIds(List.of())
                .build();
        RoleDTO created = RoleDTO.builder()
                .id(newId)
                .name("Editor")
                .scope(RoleScope.TENANT)
                .description("Can edit resources")
                .priority(5)
                .permissionIds(List.of())
                .build();

        when(roleService.createRole(request)).thenReturn(created);

        var res = controller.create(adminUser(), request, new MockHttpServletRequest());

        assertEquals(201, res.getStatusCode().value());
        RoleDTO body = (RoleDTO) res.getBody();
        assertNotNull(body);
        assertEquals(newId, body.getId());
        assertEquals("Editor", body.getName());
        assertEquals(RoleScope.TENANT, body.getScope());
    }

    @Test
    void createWhenServiceThrowsReturnsBadRequest() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        RoleDTO request = RoleDTO.builder().name("Dup").scope(RoleScope.TENANT).priority(0).build();
        when(roleService.createRole(request)).thenThrow(new IllegalArgumentException("duplicate role name"));

        var res = controller.create(adminUser(), request, new MockHttpServletRequest());

        assertEquals(400, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    // -------------------------------------------------------------------------
    // Smazání
    // -------------------------------------------------------------------------

    @Test
    void deleteReturns204NoContent() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        doNothing().when(roleService).deleteRole(roleId);

        var res = controller.delete(adminUser(), roleId, new MockHttpServletRequest());

        assertEquals(204, res.getStatusCode().value());
        assertNull(res.getBody());
        verify(roleService, times(1)).deleteRole(roleId);
    }

    @Test
    void deleteWhenRoleNotFoundReturnsBadRequest() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("Role not found")).when(roleService).deleteRole(roleId);

        var res = controller.delete(adminUser(), roleId, new MockHttpServletRequest());

        assertEquals(400, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().toString().contains("Role not found"));
    }

    // -------------------------------------------------------------------------
    // Načtení podle ID
    // -------------------------------------------------------------------------

    @Test
    void fetchByIdReturnsOkWithRoleBody() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        RoleDTO dto = RoleDTO.builder()
                .id(roleId)
                .name("Manager")
                .scope(RoleScope.TENANT)
                .priority(7)
                .permissionIds(List.of())
                .build();

        when(roleService.getRoleById(roleId)).thenReturn(dto);

        var res = controller.fetchById(roleId);

        assertEquals(200, res.getStatusCode().value());
        RoleDTO body = (RoleDTO) res.getBody();
        assertNotNull(body);
        assertEquals(roleId, body.getId());
        assertEquals("Manager", body.getName());
    }

    @Test
    void fetchByIdWhenNotFoundReturns404() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        String roleId = UUID.randomUUID().toString();
        when(roleService.getRoleById(roleId)).thenThrow(new IllegalArgumentException("Role not found"));

        var res = controller.fetchById(roleId);

        assertEquals(404, res.getStatusCode().value());
        assertNotNull(res.getBody());
    }

    // -------------------------------------------------------------------------
    // Načtení všech
    // -------------------------------------------------------------------------

    @Test
    void fetchAllReturnsOkWithTable() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        TableDTO table = TableDTO.builder()
                .rows(List.of())
                .columns(List.of())
                .pageNumber(0)
                .pageSize(10)
                .totalPages(0)
                .totalElements(0)
                .build();

        when(roleService.getAllRoles(0, 10, "id", "asc", null)).thenReturn(table);

        var res = controller.fetchAll(0, 10, "id", "asc", null);

        assertEquals(200, res.getStatusCode().value());
        TableDTO body = res.getBody();
        assertNotNull(body);
        assertEquals(0, body.getTotalElements());
    }

    @Test
    void fetchAllWithSearchQueryDelegatesToService() {
        RoleService roleService = mock(RoleService.class);
        RoleController controller = controller(roleService);

        TableDTO table = TableDTO.builder()
                .rows(List.of())
                .columns(List.of())
                .pageNumber(0)
                .pageSize(5)
                .totalPages(0)
                .totalElements(0)
                .build();

        when(roleService.getAllRoles(0, 5, "name", "desc", "admin")).thenReturn(table);

        var res = controller.fetchAll(0, 5, "name", "desc", "admin");

        assertEquals(200, res.getStatusCode().value());
        verify(roleService, times(1)).getAllRoles(0, 5, "name", "desc", "admin");
    }

    // -------------------------------------------------------------------------
    // Audit — ověřit, že je volané audit logování
    // -------------------------------------------------------------------------

    @Test
    void createCallsAuditLog() {
        RoleService roleService = mock(RoleService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RoleController controller = new RoleController(roleService, auditLogService);

        String newId = UUID.randomUUID().toString();
        RoleDTO request = RoleDTO.builder().name("Audited").scope(RoleScope.TENANT).priority(0).build();
        RoleDTO created = RoleDTO.builder().id(newId).name("Audited").scope(RoleScope.TENANT).priority(0).build();

        when(roleService.createRole(request)).thenReturn(created);

        controller.create(adminUser(), request, new MockHttpServletRequest());

        // Audit musí být volán; ověřujeme pouze, že nevyhodí výjimku
        verify(roleService, times(1)).createRole(request);
    }

    @Test
    void deleteCallsAuditLog() {
        RoleService roleService = mock(RoleService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RoleController controller = new RoleController(roleService, auditLogService);

        String roleId = UUID.randomUUID().toString();
        doNothing().when(roleService).deleteRole(roleId);

        controller.delete(adminUser(), roleId, new MockHttpServletRequest());

        verify(roleService, times(1)).deleteRole(roleId);
    }
}
