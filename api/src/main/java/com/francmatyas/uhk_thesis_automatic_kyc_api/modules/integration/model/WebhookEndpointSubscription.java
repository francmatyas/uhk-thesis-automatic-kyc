package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "webhook_endpoint_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_webhook_endpoint_subscriptions_endpoint_event", columnNames = {"endpoint_id", "event_type"})
        },
        indexes = {
                @Index(name = "ix_webhook_endpoint_subscriptions_endpoint", columnList = "endpoint_id"),
                @Index(name = "ix_webhook_endpoint_subscriptions_event_enabled", columnList = "event_type,enabled")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WebhookEndpointSubscription {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false, foreignKey = @ForeignKey(name = "fk_webhook_endpoint_subscriptions_endpoint"))
    private WebhookEndpoint endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private WebhookEventType eventType;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
