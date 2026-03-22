package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {
}
