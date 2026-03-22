package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {
    Optional<Tenant> findBySlug(String slug);
}
