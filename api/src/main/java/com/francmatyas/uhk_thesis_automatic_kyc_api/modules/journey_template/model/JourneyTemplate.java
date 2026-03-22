package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Entity
@Table(
        name = "journey_templates",
        indexes = {
                @Index(name = "ix_journey_templates_tenant", columnList = "tenant_id"),
                @Index(name = "ix_journey_templates_tenant_status", columnList = "tenant_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE journey_templates SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class JourneyTemplate extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * JSONB definice kroků a požadovaných dokumentů.
     * Příklad: {"steps":["PERSONAL_DATA","ID_DOCUMENT","SELFIE","LIVENESS","SANCTIONS_CHECK"],
     *            "requiredDocuments":["NATIONAL_ID","PASSPORT"]}
     */
    @Type(JsonType.class)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private JsonNode configJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JourneyTemplateStatus status = JourneyTemplateStatus.ACTIVE;
}
