package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JourneyTemplateRepository extends JpaRepository<JourneyTemplate, UUID> {
    Page<JourneyTemplate> findAllByTenantId(UUID tenantId, Pageable pageable);

    Optional<JourneyTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByNameAndTenantId(String name, UUID tenantId);
}