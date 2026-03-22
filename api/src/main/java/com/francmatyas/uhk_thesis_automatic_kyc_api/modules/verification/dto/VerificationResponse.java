package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(
        UUID id,
        UUID tenantId,
        UUID journeyTemplateId,
        UUID clientIdentityId,
        VerificationStatus status,
        Instant expiresAt,
        Instant completedAt,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        /** Raw token – jen při vytvoření, při dalších čteních null. */
        String token
) {
    public static VerificationResponse from(Verification v) {
        return from(v, null);
    }

    public static VerificationResponse from(Verification v, String rawToken) {
        return new VerificationResponse(
                v.getId(),
                v.getTenantId(),
                v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId() : null,
                v.getClientIdentity() != null ? v.getClientIdentity().getId() : null,
                v.getStatus(),
                v.getExpiresAt(),
                v.getCompletedAt(),
                v.getCreatedByUserId(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                rawToken
        );
    }
}
