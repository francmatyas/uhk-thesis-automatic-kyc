package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import java.util.UUID;

/**
 * Uchovává aktivního tenanta pro aktuální request.
 *
 * Provider uživatelé: typicky nemají vybraného tenanta (null), pokud nikoho neimpersonují/nevyberou.
 * Tenant uživatelé: tenantId by mělo být nastavené (z request hlavičky nebo z principalu).
 */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
