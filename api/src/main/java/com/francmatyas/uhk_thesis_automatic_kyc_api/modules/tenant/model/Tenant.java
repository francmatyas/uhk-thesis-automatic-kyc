package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tenants",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenants_slug", columnNames = {"slug"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE tenants SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 128, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", foreignKey = @ForeignKey(name = "fk_tenants_owner_user"))
    private User ownerUser;

    @Column(length = 16)
    private String region;

    @Type(JsonType.class)
    @Column(name = "settings_json", columnDefinition = "jsonb")
    private Map<String, Object> settingsJson;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "billing_customer_id", columnDefinition = "text")
    private String billingCustomerId;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "suspended_reason", length = 255)
    private String suspendedReason;
}
