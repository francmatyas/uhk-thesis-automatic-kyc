package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_sessions_jti", columnNames = {"jti"}),
        indexes = {
                @Index(name = "ix_user_sessions_user", columnList = "user_id"),
                @Index(name = "ix_user_sessions_last_seen", columnList = "last_seen_at")
        })
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"user"})
@SQLDelete(sql = "UPDATE user_sessions SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserSession extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_sessions_user"))
    private User user;

    // JWT ID (jti) pro spárování tokenu a revokaci po jednotlivých tokenech
    @Column(nullable = false, length = 64)
    private String jti;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ip_address", columnDefinition = "text")
    private String ipAddress;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "device_type", length = 64)
    private String deviceType; // mobile, tablet, desktop, etc.

    @Column(name = "device_vendor", length = 128)
    private String deviceVendor;

    @Column(name = "device_model", length = 128)
    private String deviceModel;

    @Column(name = "os_name", length = 128)
    private String osName;

    @Column(name = "os_version", length = 128)
    private String osVersion;

    @Column(name = "browser_name", length = 128)
    private String browserName;

    @Column(name = "browser_version", length = 128)
    private String browserVersion;

    @Column(name = "cpu_arch", length = 64)
    private String cpuArch;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    @Column(name = "tenant_id")
    private UUID tenantId;
}
