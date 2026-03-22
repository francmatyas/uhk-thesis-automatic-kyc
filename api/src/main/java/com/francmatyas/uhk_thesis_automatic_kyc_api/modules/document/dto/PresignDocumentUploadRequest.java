package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PresignDocumentUploadRequest {
    @NotBlank
    private String ownerType;
    @NotNull
    private UUID ownerId;
    private UUID tenantId;
    @NotBlank
    private String category;
    @NotBlank
    private String filename;
    @NotBlank
    private String contentType;
}
