package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedJsonNodeConverter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_jobs",
        indexes = {
                @Index(name = "ix_webhook_delivery_jobs_status_next", columnList = "status,next_attempt_at"),
                @Index(name = "ix_webhook_delivery_jobs_endpoint", columnList = "endpoint_id"),
                @Index(name = "ix_webhook_delivery_jobs_tenant_created", columnList = "tenant_id,created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WebhookDeliveryJob {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(name = "event_payload", nullable = false, columnDefinition = "text")
    private JsonNode eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
