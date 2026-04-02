package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateJourneyTemplateRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private JsonNode configJson;

    private JourneyTemplateStatus status;
}