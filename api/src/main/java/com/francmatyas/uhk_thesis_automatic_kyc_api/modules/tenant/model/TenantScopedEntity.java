package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model;

import java.util.UUID;

/**
 * Marker rozhraní pro entity, které patří tenantovi.
 */
public interface TenantScopedEntity {
    UUID getTenantId();
    void setTenantId(UUID tenantId);
}
