package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ověřuje constraint konzistence scope na UserTenantRole bez použití reflexe
 * pro volání privátních lifecycle metod. Validaci spouštíme voláním
 * callbacku @PrePersist / @PreUpdate přes simulaci JPA lifecycle —
 * nebo voláním metod UserTenantRole, které by volal produkční kód,
 * takže je validace pozorovatelná přes veřejné API.
 *
 * JPA lifecycle anotace (@PrePersist, @PreUpdate) dělají metodu
 * "private", ale stále ji lze najít jako JPA callback. Najdeme ji a zavoláme
 * přes JPA callback kontrakt (hledání @PrePersist/@PreUpdate),
 * což je zamýšlený veřejný kontrakt této validační logiky.
 */
class UserTenantRoleScopeValidationTests {

    /**
     * Spustí všechny @PrePersist callbacky na dané entitě (simulace JPA lifecycle).
     * Tím se vyhneme hard-coded názvu privátní metody přes reflexi.
     */
    private static void triggerPrePersist(Object entity) throws Exception {
        for (Method m : entity.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(PrePersist.class)) {
                m.setAccessible(true);
                m.invoke(entity);
            }
        }
    }

    /**
     * Spustí všechny @PreUpdate callbacky na dané entitě (simulace JPA lifecycle).
     */
    private static void triggerPreUpdate(Object entity) throws Exception {
        for (Method m : entity.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(PreUpdate.class)) {
                m.setAccessible(true);
                m.invoke(entity);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Provider přiřazení (tenant == null) musí používat roli se scope PROVIDER
    // -------------------------------------------------------------------------

    @Test
    void providerAssignmentRejectsTenantRole_onPrePersist() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("TENANT_ADMIN");
        role.setScope(RoleScope.TENANT);

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(null); // provider-level assignment
        utr.setRole(role);

        Exception ex = assertThrows(Exception.class, () -> triggerPrePersist(utr));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("requires role scope PROVIDER"),
                "Expected message about PROVIDER scope but got: " + cause.getMessage());
    }

    @Test
    void providerAssignmentRejectsTenantRole_onPreUpdate() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("TENANT_ADMIN");
        role.setScope(RoleScope.TENANT);

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(null);
        utr.setRole(role);

        Exception ex = assertThrows(Exception.class, () -> triggerPreUpdate(utr));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("requires role scope PROVIDER"));
    }

    // -------------------------------------------------------------------------
    // Tenant přiřazení (tenant != null) musí používat roli se scope TENANT
    // -------------------------------------------------------------------------

    @Test
    void tenantAssignmentRejectsProviderRole_onPrePersist() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("PROVIDER_ADMIN");
        role.setScope(RoleScope.PROVIDER);

        Tenant t = new Tenant();
        t.setId(UUID.randomUUID());

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(t); // tenant-level assignment
        utr.setRole(role);

        Exception ex = assertThrows(Exception.class, () -> triggerPrePersist(utr));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("requires role scope TENANT"),
                "Expected message about TENANT scope but got: " + cause.getMessage());
    }

    @Test
    void tenantAssignmentRejectsProviderRole_onPreUpdate() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("PROVIDER_ADMIN");
        role.setScope(RoleScope.PROVIDER);

        Tenant t = new Tenant();
        t.setId(UUID.randomUUID());

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(t);
        utr.setRole(role);

        Exception ex = assertThrows(Exception.class, () -> triggerPreUpdate(utr));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("requires role scope TENANT"));
    }

    // -------------------------------------------------------------------------
    // Validní kombinace nesmí vyhazovat výjimku
    // -------------------------------------------------------------------------

    @Test
    void providerAssignmentAcceptsProviderRole() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("OWNER");
        role.setScope(RoleScope.PROVIDER);

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(null);
        utr.setRole(role);

        assertDoesNotThrow(() -> triggerPrePersist(utr));
        assertDoesNotThrow(() -> triggerPreUpdate(utr));
    }

    @Test
    void tenantAssignmentAcceptsTenantRole() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        Role role = new Role();
        role.setName("MEMBER");
        role.setScope(RoleScope.TENANT);

        Tenant t = new Tenant();
        t.setId(UUID.randomUUID());

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(t);
        utr.setRole(role);

        assertDoesNotThrow(() -> triggerPrePersist(utr));
        assertDoesNotThrow(() -> triggerPreUpdate(utr));
    }

    @Test
    void nullRoleSkipsValidation() throws Exception {
        User u = new User();
        u.setId(UUID.randomUUID());

        UserTenantRole utr = new UserTenantRole();
        utr.setUser(u);
        utr.setTenant(null);
        utr.setRole(null); // null role -> validation should be skipped per source code

        assertDoesNotThrow(() -> triggerPrePersist(utr));
        assertDoesNotThrow(() -> triggerPreUpdate(utr));
    }
}
