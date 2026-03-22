package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(
        name = "api_keys",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_api_keys_public_key", columnNames = {"public_key"})
        },
        indexes = {
                @Index(name = "ix_api_keys_tenant", columnList = "tenant_id"),
                @Index(name = "ix_api_keys_status", columnList = "status")
        }
)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"tenant"})
@SQLDelete(sql = "UPDATE api_keys SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class ApiKey extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_api_keys_tenant"))
    private Tenant tenant;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "public_key", nullable = false, length = 128)
    private String publicKey;

    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}

