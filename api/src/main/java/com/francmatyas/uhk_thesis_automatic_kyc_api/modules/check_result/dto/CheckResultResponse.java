package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResultStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckResultResponse(
        UUID id,
        UUID verificationId,
        CheckType checkType,
        CheckResultStatus status,
        BigDecimal score,
        JsonNode detailsJson
) {
    public static CheckResultResponse from(CheckResult r) {
        return new CheckResultResponse(
                r.getId(),
                r.getVerification() != null ? r.getVerification().getId() : null,
                r.getCheckType(),
                r.getStatus(),
                r.getScore(),
                r.getDetailsJson()
        );
    }
}