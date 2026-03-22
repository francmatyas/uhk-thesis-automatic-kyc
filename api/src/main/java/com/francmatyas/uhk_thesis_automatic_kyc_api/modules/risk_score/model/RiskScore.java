package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model;

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

import java.util.UUID;

@Entity
@Table(
        name = "risk_scores",
        indexes = {
                @Index(name = "ix_risk_scores_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_risk_scores_verification",
                columnNames = {"verification_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE risk_scores SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class RiskScore extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_risk_scores_verification"))
    private Verification verification;

    /** Agregované skóre v rozsahu [0, 100]; vyšší = rizikovější. */
    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel level;

    /** JSONB rozpad ukazující, jak bylo vypočteno finální skóre. */
    @Type(JsonType.class)
    @Column(name = "breakdown_json", columnDefinition = "jsonb")
    private JsonNode breakdownJson;
}
