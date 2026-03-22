package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "user_tenant_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_tenant_role",
                columnNames = {"user_id", "role_id", "tenant_id"}
        ),
        indexes = {
                @Index(name = "ix_user_tenant_roles_user", columnList = "user_id"),
                @Index(name = "ix_user_tenant_roles_role", columnList = "role_id"),
                @Index(name = "ix_user_tenant_roles_tenant", columnList = "tenant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"user", "role", "tenant"})
@SQLDelete(sql = "UPDATE user_tenant_roles SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserTenantRole extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_tenant_roles_user"))
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_tenant_roles_role"))
    private Role role;

    /**
     * NULL znamená přiřazení na úrovni poskytovatele.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", foreignKey = @ForeignKey(name = "fk_user_tenant_roles_tenant"))
    private Tenant tenant;

    @PrePersist
    @PreUpdate
    private void validateScopeConsistency() {
        if (role == null) return;

        RoleScope s = role.getScope();

        // tenant == null => přiřazení poskytovateli => role musí být PROVIDER
        if (tenant == null) {
            if (s != RoleScope.PROVIDER) {
                throw new IllegalStateException("Provider assignment (tenant_id=NULL) requires role scope PROVIDER");
            }
            return;
        }

        // tenant != null => přiřazení tenantovi => role musí být TENANT
        if (s != RoleScope.TENANT) {
            throw new IllegalStateException("Tenant assignment (tenant_id!=NULL) requires role scope TENANT");
        }
    }
}
