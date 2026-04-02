package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class FlowLivenessRequest {
    @NotEmpty
    private List<@NotNull @Valid LivenessImage> images;

    @Data
    public static class LivenessImage {
        @NotNull
        private UUID documentId;
        /** One of: center, left, right, top, bottom */
        @NotBlank
        private String position;
    }
}