package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.dto.CheckResultResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.dto.RiskScoreResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStep;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VerificationResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        UUID journeyTemplateId,
        String journeyTemplateName,
        VerificationStatus status,
        BigDecimal overallScore,
        RiskScoreResponse riskScore,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdByUserId,
        String clientName,
        ClientIdentity clientIdentity,
        List<StepResponse> steps
) {

    public record StepResponse(
            UUID id,
            VerificationStepType stepType,
            VerificationStepStatus status,
            Instant completedAt,
            JsonNode detailsJson,
            List<CheckResultResponse> checkResults
    ) {
        static StepResponse from(VerificationStep step, List<CheckResultResponse> checkResults) {
            return new StepResponse(
                    step.getId(),
                    step.getStepType(),
                    step.getStatus(),
                    step.getCompletedAt(),
                    step.getDetailsJson(),
                    checkResults
            );
        }
    }

    public static VerificationResponse from(Verification v, String tenantName,
                                             List<VerificationStep> steps,
                                             List<CheckResult> checkResults,
                                             RiskScoreResponse riskScore,
                                             boolean includeClientIdentity) {
        Map<VerificationStepType, List<CheckResultResponse>> resultsByStep = new HashMap<>();
        for (CheckResult cr : checkResults) {
            VerificationStepType stepType = checkTypeToStepType(cr.getCheckType());
            resultsByStep.computeIfAbsent(stepType, k -> new ArrayList<>())
                         .add(CheckResultResponse.from(cr));
        }

        List<StepResponse> stepResponses = steps.stream()
                .map(s -> StepResponse.from(s, resultsByStep.getOrDefault(s.getStepType(), List.of())))
                .toList();

        return new VerificationResponse(
                v.getId(),
                v.getTenantId(),
                tenantName,
                v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId() : null,
                v.getJourneyTemplate() != null ? v.getJourneyTemplate().getName() : null,
                v.getStatus(),
                v.getOverallScore(),
                riskScore,
                v.getExpiresAt(),
                v.getCompletedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                v.getCreatedByUserId(),
                includeClientIdentity && v.getClientIdentity() != null
                        ? v.getClientIdentity().getFirstName() + " " + v.getClientIdentity().getLastName() : null,
                includeClientIdentity ? v.getClientIdentity() : null,
                stepResponses
        );
    }

    private static VerificationStepType checkTypeToStepType(CheckType type) {
        return switch (type) {
            case PERSONAL_INFO, DOC_DATA_MATCH -> VerificationStepType.PERSONAL_INFO;
            case DOC_OCR -> VerificationStepType.DOC_OCR;
            case FACE_MATCH -> VerificationStepType.FACE_MATCH;
            case LIVENESS -> VerificationStepType.LIVENESS;
            case SANCTIONS, PEP -> VerificationStepType.AML_SCREEN;
            case EMAIL_VERIFICATION -> VerificationStepType.EMAIL_VERIFICATION;
            case PHONE_VERIFICATION -> VerificationStepType.PHONE_VERIFICATION;
            case AML_QUESTIONNAIRE -> VerificationStepType.AML_QUESTIONNAIRE;
        };
    }
}
