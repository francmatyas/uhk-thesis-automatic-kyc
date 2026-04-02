package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskLevel;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskScore;

import java.time.Instant;
import java.util.UUID;

public record RiskScoreResponse(
        UUID id,
        UUID verificationId,
        int overallScore,
        RiskLevel level,
        JsonNode breakdownJson
) {
    public static RiskScoreResponse from(RiskScore r) {
        return new RiskScoreResponse(
                r.getId(),
                r.getVerification() != null ? r.getVerification().getId() : null,
                r.getOverallScore(),
                r.getLevel(),
                r.getBreakdownJson()
        );
    }
}