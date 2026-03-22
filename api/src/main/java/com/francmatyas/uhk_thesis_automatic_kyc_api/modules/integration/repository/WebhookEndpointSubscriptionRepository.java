package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpoint;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointSubscription;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointSubscriptionRepository extends JpaRepository<WebhookEndpointSubscription, UUID> {
    List<WebhookEndpointSubscription> findByEndpointIdOrderByEventTypeAsc(UUID endpointId);

    void deleteByEndpointId(UUID endpointId);

    @Query("""
            select count(s) from WebhookEndpointSubscription s
            where s.endpoint.tenant.id = :tenantId
            """)
    long countByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
            select s.endpoint from WebhookEndpointSubscription s
            where s.endpoint.tenant.id = :tenantId
              and s.enabled = true
              and s.endpoint.status = com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEndpointStatus.ACTIVE
              and s.eventType = :eventType
            """)
    List<WebhookEndpoint> findActiveEndpointsByTenantIdAndEventType(
            @Param("tenantId") UUID tenantId,
            @Param("eventType") WebhookEventType eventType
    );
}
