package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateApiKeyRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
}
