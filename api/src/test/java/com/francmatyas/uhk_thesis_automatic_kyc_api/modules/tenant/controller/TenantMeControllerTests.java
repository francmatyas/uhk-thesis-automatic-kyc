package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto.TenantMemberUpsertRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantMeControllerTests {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void getCurrentTenantReturnsTenantWithMembers() {
        TenantService tenantService = mock(TenantService.class);
        TenantMeController controller = new TenantMeController(tenantService, mock(AuditLogService.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProviderUser(false);

        UUID tenantId = UUID.randomUUID();
        Map<String, Object> serviceBody = Map.of(
                "id", tenantId,
                "name", "Acme",
                "slug", "acme",
                "status", "ACTIVE",
                "members", List.of()
        );
        when(tenantService.getCurrentTenant(user)).thenReturn(serviceBody);

        var res = controller.getCurrentTenant(user);

        assertEquals(200, res.getStatusCode().value());
        assertEquals(serviceBody, res.getBody());
    }

    @Test
    void updateCurrentTenantAllowsOwnerToChangeNameAndSlug() {
        TenantService tenantService = mock(TenantService.class);
        TenantMeController controller = new TenantMeController(tenantService, mock(AuditLogService.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProviderUser(false);

        TenantMeController.UpdateTenantRequest req = new TenantMeController.UpdateTenantRequest();
        req.name = "New Name";
        req.slug = "new-name";

        Map<String, Object> serviceBody = Map.of(
                "id", UUID.randomUUID(),
                "name", "New Name",
                "slug", "new-name",
                "status", "ACTIVE"
        );
        when(tenantService.updateCurrentTenant(user, "New Name", "new-name", null)).thenReturn(serviceBody);

        var res = controller.updateCurrentTenant(user, req, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
        assertEquals(serviceBody, res.getBody());
        verify(tenantService).updateCurrentTenant(user, "New Name", "new-name", null);
    }

    @Test
    void updateCurrentTenantRejectsMemberWithoutAdminOrOwnerRole() {
        TenantService tenantService = mock(TenantService.class);
        TenantMeController controller = new TenantMeController(tenantService, mock(AuditLogService.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProviderUser(false);

        TenantMeController.UpdateTenantRequest req = new TenantMeController.UpdateTenantRequest();
        req.name = "Any";

        when(tenantService.updateCurrentTenant(user, "Any", null, null))
                .thenThrow(new NoSuchElementException("tenant_not_found"));

        var res = controller.updateCurrentTenant(user, req, new MockHttpServletRequest());

        assertEquals(404, res.getStatusCode().value());
        assertEquals(Map.of("error", "tenant_not_found"), res.getBody());
    }

    @Test
    void updateCurrentTenantAllowsPermissionWithoutOwnerOrAdminRole() {
        TenantService tenantService = mock(TenantService.class);
        TenantMeController controller = new TenantMeController(tenantService, mock(AuditLogService.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProviderUser(false);

        TenantMeController.UpdateTenantRequest req = new TenantMeController.UpdateTenantRequest();
        req.name = "Tenant Updated";

        when(tenantService.updateCurrentTenant(user, "Tenant Updated", null, null))
                .thenReturn(Map.of(
                        "id", UUID.randomUUID(),
                        "name", "Tenant Updated",
                        "slug", "tenant",
                        "status", "ACTIVE"
                ));

        var res = controller.updateCurrentTenant(user, req, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void updateCurrentTenantForwardsMembersPayload() {
        TenantService tenantService = mock(TenantService.class);
        TenantMeController controller = new TenantMeController(tenantService, mock(AuditLogService.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProviderUser(false);

        TenantMeController.UpdateTenantRequest req = new TenantMeController.UpdateTenantRequest();
        TenantMemberUpsertRequest member = new TenantMemberUpsertRequest();
        member.setId(UUID.randomUUID().toString());
        member.setRoles(List.of("OWNER"));
        req.members = new ArrayList<>();
        req.members.add(member);

        when(tenantService.updateCurrentTenant(user, null, null, req.members))
                .thenReturn(Map.of(
                        "id", UUID.randomUUID(),
                        "name", "Tenant Updated",
                        "slug", "tenant",
                        "status", "ACTIVE"
                ));

        var res = controller.updateCurrentTenant(user, req, new MockHttpServletRequest());

        assertEquals(200, res.getStatusCode().value());
        verify(tenantService).updateCurrentTenant(user, null, null, req.members);
    }
}
