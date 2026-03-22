package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateWebhookEndpointRequest {
    @NotBlank
    @Size(max = 4096)
    private String url;

    @Size(min = 16, max = 255)
    private String secret;

    @Size(max = 32)
    private List<@Size(max = 64) String> eventTypes;
}
