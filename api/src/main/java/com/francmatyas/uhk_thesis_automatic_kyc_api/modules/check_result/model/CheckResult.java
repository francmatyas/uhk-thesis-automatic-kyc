package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedJsonNodeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "check_results",
        indexes = {
                @Index(name = "ix_check_results_verification", columnList = "verification_id"),
                @Index(name = "ix_check_results_tenant_type", columnList = "tenant_id, check_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE check_results SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class CheckResult extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_check_results_verification"))
    private Verification verification;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 32)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CheckResultStatus status;

    /** Skóre důvěry/podobnosti v rozsahu [0, 1]; null pokud není použitelné. */
    @Column(precision = 6, scale = 4)
    private BigDecimal score;

    /** Surový výstup z Python workeru – diagnostika, metriky apod. Šifrované při uložení. */
    @Convert(converter = EncryptedJsonNodeConverter.class)
    @Column(name = "details_json", columnDefinition = "text")
    private JsonNode detailsJson;
}
