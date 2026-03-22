package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpoint;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantId(UUID tenantId);
    List<WebhookEndpoint> findByTenantIdAndStatus(UUID tenantId, WebhookEndpointStatus status);
    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, UUID tenantId);
}
