package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_role_slug", columnNames = "slug"),
                @UniqueConstraint(name = "uq_role_name_scope", columnNames = {"name", "scope"})
        }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"rolePermissions", "userRoles"})
@SQLDelete(sql = "UPDATE roles SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class Role extends BaseEntity {
    @Column(nullable = false, length = 64)
    private String name; // e.g. "ADMIN"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private RoleScope scope = RoleScope.TENANT;

    @Column(nullable = false, length = 96)
    private String slug; // e.g. "TENANT_ADMIN"

    @Column(length = 255)
    private String description;

    // Vyšší hodnota = priorita při konfliktech v UI nebo při výběru výchozí politiky
    @Column(nullable = false)
    private int priority = 0;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RolePermission> rolePermissions = new LinkedHashSet<>();

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new LinkedHashSet<>();

    @PrePersist
    @PreUpdate
    private void syncFields() {
        // Normalizace názvu při zachování uživatelsky zobrazované podoby
        if (this.name != null) {
            this.name = this.name.trim();
        }

        // Sestavení slugu ze scope + názvu
        String scopePart = (this.scope == null) ? "" : this.scope.name();
        String namePart = (this.name == null) ? "" : this.name;

        String combined = scopePart + "_" + namePart;
        this.slug = combined
                .trim()
                .toUpperCase()
                .replaceAll("\\s+", "_");
    }
}
