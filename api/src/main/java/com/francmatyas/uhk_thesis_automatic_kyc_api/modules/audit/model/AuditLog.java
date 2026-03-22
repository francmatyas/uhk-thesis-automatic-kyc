package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedJsonNodeConverter;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "ix_audit_logs_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "ix_audit_logs_entity_created", columnList = "entity_type,entity_id,created_at"),
                @Index(name = "ix_audit_logs_actor_user_created", columnList = "actor_user_id,created_at"),
                @Index(name = "ix_audit_logs_correlation", columnList = "correlation_id"),
                @Index(name = "ix_audit_logs_created", columnList = "created_at")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Immutable
public class AuditLog {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private AuditActorType actorType;

    @Column(name = "actor_api_key_id")
    private UUID actorApiKeyId;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false, columnDefinition = "text")
    private String entityId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(name = "old_value", columnDefinition = "text")
    private JsonNode oldValue;

    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(name = "new_value", columnDefinition = "text")
    private JsonNode newValue;

    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ip_address", columnDefinition = "text")
    private String ipAddress;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    @Builder.Default
    private AuditResult result = AuditResult.SUCCESS;

    @Column(name = "error_code", length = 64)
    private String errorCode;
}
