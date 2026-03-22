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
public class VerificationListDTO {

    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "Status", order = 2, type = DisplayFieldType.REFERENCE,
            referenceKey = "id", referenceTemplate = "/p/verifications/{id}")
    private String status;

    @DisplayField(header = "Tenant", order = 3, sortable = false, filterable = false)
    private String tenantId;

    @DisplayField(header = "Template", order = 4, sortable = false, filterable = false)
    private String journeyTemplateId;

    @DisplayField(header = "Created", order = 5, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;

    @DisplayField(header = "Expires", order = 6, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant expiresAt;
}
