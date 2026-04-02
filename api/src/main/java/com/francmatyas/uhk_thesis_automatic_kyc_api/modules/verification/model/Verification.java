package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "verifications",
        indexes = {
                @Index(name = "ix_verifications_tenant", columnList = "tenant_id"),
                @Index(name = "ix_verifications_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "ix_verifications_client_identity", columnList = "client_identity_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_verifications_token_hash",
                columnNames = {"verification_token_hash"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE verifications SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class Verification extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_template_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_verifications_journey_template"))
    private JourneyTemplate journeyTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_identity_id",
            foreignKey = @ForeignKey(name = "fk_verifications_client_identity"))
    private ClientIdentity clientIdentity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.INITIATED;

    /**
     * SHA-256 hex hash surového verifikačního tokenu použitého v /flow/{token}.
     * Raw token se pošle klientovi a nikdy se neukládá.
     */
    @Column(name = "verification_token_hash", nullable = false, length = 128)
    private String verificationTokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Uživatel (tenant operátor nebo provider), který tuto verifikaci inicioval. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    /** Celkové skóre KYC verifikace v rozsahu [0, 1]; vypočítáno po dokončení automatických kontrol. */
    @Column(name = "overall_score", precision = 6, scale = 4)
    private BigDecimal overallScore;
}
