package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto;

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
public class ApiKeyListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "Name", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/api-keys/{id}")
    private String name;

    @DisplayField(header = "Public Key", order = 3)
    private String publicKey;

    @DisplayField(header = "Status", order = 4, type = DisplayFieldType.ENUM)
    private String status;

    @DisplayField(header = "Last Used", order = 5, type = DisplayFieldType.DATETIME)
    private Instant lastUsedAt;

    @DisplayField(header = "Created At", order = 6, type = DisplayFieldType.DATETIME)
    private Instant createdAt;
}
