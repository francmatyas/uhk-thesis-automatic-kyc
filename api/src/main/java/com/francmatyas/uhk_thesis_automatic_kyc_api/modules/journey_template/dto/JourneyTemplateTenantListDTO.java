package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayField;
import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateTenantListDTO {

    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "Name", order = 2, type = DisplayFieldType.REFERENCE,
            referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/journey-templates/{id}")
    private String name;

    @DisplayField(header = "Status", order = 3, type = DisplayFieldType.ENUM, sortable = true)
    private String status;

    @DisplayField(header = "Created", order = 4, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;
}
