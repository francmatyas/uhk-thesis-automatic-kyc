package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateVerificationRequest {

    @NotNull
    private UUID journeyTemplateId;

    private Instant expiresAt;

    /** Vlastní referenční ID klienta na straně tenanta, např. interní customer ID. */
    @Size(max = 255)
    private String externalReference;
}
