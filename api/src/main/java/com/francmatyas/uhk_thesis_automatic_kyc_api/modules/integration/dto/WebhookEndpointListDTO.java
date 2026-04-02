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
public class WebhookEndpointListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "moduleDefinitions.webhooks.columns.url", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/webhooks/{id}")
    private String url;

    @DisplayField(header = "moduleDefinitions.webhooks.columns.status", order = 3, type = DisplayFieldType.ENUM)
    private String status;

    @DisplayField(header = "moduleDefinitions.webhooks.columns.lastDeliveryAt", order = 4, type = DisplayFieldType.DATETIME)
    private Instant lastDeliveryAt;

    @DisplayField(header = "moduleDefinitions.webhooks.columns.createdAt", order = 5, type = DisplayFieldType.DATETIME)
    private Instant createdAt;
}
