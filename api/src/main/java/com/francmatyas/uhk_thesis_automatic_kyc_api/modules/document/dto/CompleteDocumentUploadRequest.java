package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto;

import lombok.Data;

@Data
public class CompleteDocumentUploadRequest {
    private Long sizeBytes;
    private String checksum;
}
