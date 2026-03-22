package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_role_perm", columnNames = {"role_id", "permission_id"}),
        indexes = {
                @Index(name = "ix_role_permissions_role", columnList = "role_id"),
                @Index(name = "ix_role_permissions_permission", columnList = "permission_id")
        })
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE role_permissions SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class RolePermission extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_roleperm_role"))
    private Role role;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_roleperm_perm"))
    private Permission permission;
}
