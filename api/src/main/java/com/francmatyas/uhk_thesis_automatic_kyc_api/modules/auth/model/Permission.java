package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.util.*;

@Entity
@Table(name = "permissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_perm_resource_action",
                columnNames = {"resource", "action"}))
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE permissions SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class Permission extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String resource;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 128)
    private String label;

    @Column(length = 256)
    private String description;

    @Type(JsonType.class)
    @Column(name = "constraint_json", columnDefinition = "jsonb")
    private Map<String, Object> constraintJson;

    @PrePersist
    @PreUpdate
    private void syncLabel() {
        StringBuilder builder = new StringBuilder();

        if (resource != null && !resource.isBlank()) {
            builder.append(resource.trim());
        }
        if (action != null && !action.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(":");
            }
            builder.append(action.trim());
        }

        this.label = builder.toString();
    }
}
