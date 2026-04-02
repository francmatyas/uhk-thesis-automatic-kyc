package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

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
public class VerificationTenantListDTO {

    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "moduleDefinitions.verifications.columns.clientIdentity", order = 2, type = DisplayFieldType.REFERENCE,
            referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/verifications/{id}")
    private String clientName;

    @DisplayField(header = "moduleDefinitions.verifications.columns.status", order = 3, sortable = true, filterable = false)
    private String status;

    @DisplayField(header = "Template Id", order = 4, hidden = true, sortable = false, filterable = false)
    private String journeyTemplateId;

    @DisplayField(header = "moduleDefinitions.verifications.columns.journeyTemplate", order = 5, type = DisplayFieldType.REFERENCE,
            referenceKey = "journeyTemplateId", referenceTemplate = "/t/{tenantSlug}/journey-templates/{journeyTemplateId}")
    private String journeyTemplateName;

    @DisplayField(header = "moduleDefinitions.verifications.columns.createdAt", order = 6, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;

    @DisplayField(header = "moduleDefinitions.verifications.columns.expiresAt", order = 7, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant expiresAt;
}
