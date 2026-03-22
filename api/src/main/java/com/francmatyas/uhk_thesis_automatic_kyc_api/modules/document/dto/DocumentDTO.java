package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.DocumentKind;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DocumentDTO {
    private UUID id;
    private String ownerType;
    private UUID ownerId;
    private UUID tenantId;
    private String category;
    private DocumentKind kind;
    private DocumentStatus status;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String checksum;
    private String storageKey;
    private String publicUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
