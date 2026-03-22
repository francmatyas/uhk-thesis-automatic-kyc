package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record JourneyTemplateResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        JsonNode configJson,
        JourneyTemplateStatus status
) {
    public static JourneyTemplateResponse from(JourneyTemplate t) {
        return new JourneyTemplateResponse(
                t.getId(), t.getTenantId(), t.getName(), t.getDescription(),
                t.getConfigJson(), t.getStatus()
        );
    }
}