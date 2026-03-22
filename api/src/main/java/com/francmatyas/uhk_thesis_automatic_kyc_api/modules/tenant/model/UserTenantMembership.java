package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Propojuje uživatelský účet s tenantem (membership). Role jsou uložené zvlášť v user_tenant_roles.
 */
@Entity
@Table(
        name = "user_tenant_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_tenant_membership", columnNames = {"user_id", "tenant_id"}),
        indexes = {
                @Index(name = "ix_user_tenant_memberships_user", columnList = "user_id"),
                @Index(name = "ix_user_tenant_memberships_tenant", columnList = "tenant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"user", "tenant"})
@SQLDelete(sql = "UPDATE user_tenant_memberships SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserTenantMembership extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_tenant_memberships_user"))
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_tenant_memberships_tenant"))
    private Tenant tenant;

    /**
     * Volitelné: označit jednoho tenanta jako výchozího pro tohoto uživatele.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;
}
