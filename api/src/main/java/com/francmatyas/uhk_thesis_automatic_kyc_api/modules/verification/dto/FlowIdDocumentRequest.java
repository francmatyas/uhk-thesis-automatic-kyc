package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class FlowIdDocumentRequest {
    @NotNull
    private DocumentType documentType;
    @NotNull
    private UUID frontDocumentId;
    /** Required for CZECH_ID, ignored for PASSPORT. */
    private UUID backDocumentId;
}