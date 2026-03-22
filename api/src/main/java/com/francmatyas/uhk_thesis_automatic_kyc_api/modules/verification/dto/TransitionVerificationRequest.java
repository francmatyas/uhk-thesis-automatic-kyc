package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransitionVerificationRequest {

    @NotNull
    private VerificationStatus status;
}