package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "verification_steps",
        indexes = {
                @Index(name = "ix_verification_steps_verification", columnList = "verification_id"),
                @Index(name = "ix_verification_steps_tenant", columnList = "tenant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE verification_steps SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class VerificationStep extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_verification_steps_verification"))
    private Verification verification;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 32)
    private VerificationStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VerificationStepStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Type(JsonType.class)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private JsonNode detailsJson;
}