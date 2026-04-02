package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowPresignRequest {
    @NotBlank
    private String filename;
    @NotBlank
    private String contentType;
    @NotBlank
    private String category;
}