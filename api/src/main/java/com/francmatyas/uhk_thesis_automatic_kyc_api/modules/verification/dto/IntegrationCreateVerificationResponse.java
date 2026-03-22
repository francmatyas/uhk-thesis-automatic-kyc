package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record IntegrationCreateVerificationResponse(
        UUID id,
        String verificationUrl,
        VerificationStatus status,
        Instant expiresAt
) {
}