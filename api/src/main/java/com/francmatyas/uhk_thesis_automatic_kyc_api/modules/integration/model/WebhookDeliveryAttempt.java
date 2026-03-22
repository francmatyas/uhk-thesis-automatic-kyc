package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts",
        indexes = {
                @Index(name = "ix_webhook_delivery_attempts_job", columnList = "delivery_job_id,requested_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_job_id", nullable = false)
    private UUID deliveryJobId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;
}
