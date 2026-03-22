package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresignDocumentUploadResponse {
    private UUID documentId;
    private String uploadUrl;
    private String storageKey;
    private String publicUrl;
    private Instant uploadExpiresAt;
}
