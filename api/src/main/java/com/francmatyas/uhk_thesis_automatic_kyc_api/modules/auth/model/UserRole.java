package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;


@Entity
@Table(name = "user_roles",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_role_scope", columnNames = {"user_id", "role_id", "scope_id"}),
        indexes = {
                @Index(name = "ix_user_roles_user", columnList = "user_id"),
                @Index(name = "ix_user_roles_role", columnList = "role_id"),
                @Index(name = "ix_user_roles_scope", columnList = "scope_id")
        })
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SQLDelete(sql = "UPDATE user_roles SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class UserRole extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_userrole_user"))
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_userrole_role"))
    private Role role;

    // Volitelný scope (např. organizace/projekt). Null = globální role.
    @Column(name = "scope_id")
    private UUID scopeId;
}
