package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "verification_otps",
        indexes = {
                @Index(name = "ix_verification_otps_verification", columnList = "verification_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE verification_otps SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class VerificationOtp extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_verification_otps_verification"))
    private Verification verification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OtpType type;

    /** SHA-256 hash surového OTP kódu. */
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    /** Zašifrovaná e-mailová adresa nebo telefonní číslo, pro které bylo OTP vystaveno. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact")
    private String contact;

    /** Telefonní předvolba (např. "+420"), vyplněno pouze pro OTP typu PHONE. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dial_code")
    private String dialCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(nullable = false)
    private int attempts;
}
