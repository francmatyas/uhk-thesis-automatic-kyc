package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryJob;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryJobRepository extends JpaRepository<WebhookDeliveryJob, UUID> {
    Page<WebhookDeliveryJob> findByStatusInAndNextAttemptAtLessThanEqual(
            List<WebhookDeliveryStatus> statuses,
            Instant dueAt,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from WebhookDeliveryJob j where j.id = :id")
    Optional<WebhookDeliveryJob> findByIdForDispatch(@Param("id") UUID id);
}
