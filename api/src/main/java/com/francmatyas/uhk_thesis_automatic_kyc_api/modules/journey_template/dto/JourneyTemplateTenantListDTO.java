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

    @DisplayField(header = "Id", order = 2, type = DisplayFieldType.MONO, copyable = true)
    private String id;

    @DisplayField(header = "moduleDefinitions.journeyTemplates.columns.name", order = 1, type = DisplayFieldType.REFERENCE,
            referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/journey-templates/{id}")
    private String name;

    @DisplayField(header = "moduleDefinitions.journeyTemplates.columns.status", order = 3, type = DisplayFieldType.ENUM, sortable = true)
    private String status;

    @DisplayField(header = "moduleDefinitions.journeyTemplates.columns.createdAt", order = 4, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;
}
