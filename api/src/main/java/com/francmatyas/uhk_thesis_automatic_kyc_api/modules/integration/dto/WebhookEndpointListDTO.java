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

    @DisplayField(header = "URL", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/webhooks/{id}")
    private String url;

    @DisplayField(header = "Status", order = 3, type = DisplayFieldType.ENUM)
    private String status;

    @DisplayField(header = "Last Delivery", order = 4, type = DisplayFieldType.DATETIME)
    private Instant lastDeliveryAt;

    @DisplayField(header = "Created At", order = 5, type = DisplayFieldType.DATETIME)
    private Instant createdAt;
}
