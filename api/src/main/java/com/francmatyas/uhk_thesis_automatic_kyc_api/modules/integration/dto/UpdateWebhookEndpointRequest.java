package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateWebhookEndpointRequest {
    @Size(max = 20)
    private String status;

    @Size(max = 32)
    private List<@Size(max = 64) String> eventTypes;
}
