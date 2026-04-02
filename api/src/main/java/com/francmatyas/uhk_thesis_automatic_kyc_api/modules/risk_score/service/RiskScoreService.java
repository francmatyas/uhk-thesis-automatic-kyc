package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskLevel;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskScore;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.repository.RiskScoreRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskScoreService {

    private static final Map<CheckType, BigDecimal> SCORE_WEIGHTS = Map.of(
            CheckType.DOC_OCR,        new BigDecimal("0.35"),
            CheckType.FACE_MATCH,     new BigDecimal("0.30"),
            CheckType.LIVENESS,       new BigDecimal("0.20"),
            CheckType.DOC_DATA_MATCH, new BigDecimal("0.15")
    );

    private final RiskScoreRepository repository;
    private final ObjectMapper objectMapper;

    public Optional<RiskScore> findByVerification(UUID verificationId) {
        return repository.findByVerificationId(verificationId);
    }

    /**
     * Vypočítá a uloží rizikové skóre pro danou verifikaci.
     * Konvertuje verifikační skóre [0,1] (vyšší = lepší) na rizikové skóre [0,100] (vyšší = rizikovější).
     */
    @Transactional
    public RiskScore computeAndSave(Verification verification, BigDecimal verificationScore, List<CheckResult> results) {
        int riskOverallScore = toRiskScore(verificationScore);
        RiskLevel level = resolveLevel(riskOverallScore);
        ObjectNode breakdown = buildBreakdown(results, verificationScore, riskOverallScore);

        RiskScore rs = RiskScore.builder()
                .tenantId(verification.getTenantId())
                .verification(verification)
                .overallScore(riskOverallScore)
                .level(level)
                .breakdownJson(breakdown)
                .build();
        return repository.save(rs);
    }

    @Transactional
    public RiskScore save(RiskScore riskScore) {
        return repository.save(riskScore);
    }

    // ------------------------------------------------------------------

    /** Invertuje verifikační skóre [0,1] na rizikové skóre [0,100]. */
    private static int toRiskScore(BigDecimal verificationScore) {
        if (verificationScore == null) {
            return 100;
        }
        int qualityPercent = verificationScore.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return 100 - qualityPercent;
    }

    private static RiskLevel resolveLevel(int riskScore) {
        if (riskScore <= 33) return RiskLevel.LOW;
        if (riskScore <= 66) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    private ObjectNode buildBreakdown(List<CheckResult> results, BigDecimal verificationScore, int riskScore) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode checks = root.putArray("checks");

        for (CheckResult r : results) {
            BigDecimal weight = SCORE_WEIGHTS.get(r.getCheckType());
            ObjectNode entry = checks.addObject();
            entry.put("checkType", r.getCheckType().name());
            entry.put("status", r.getStatus().name());
            if (r.getScore() != null) {
                entry.put("score", r.getScore());
            } else {
                entry.putNull("score");
            }
            if (weight != null) {
                entry.put("weight", weight);
                if (r.getScore() != null) {
                    entry.put("weightedContribution",
                            weight.multiply(r.getScore()).setScale(4, RoundingMode.HALF_UP));
                } else {
                    entry.putNull("weightedContribution");
                }
            } else {
                entry.putNull("weight");
                entry.putNull("weightedContribution");
            }
        }

        if (verificationScore != null) {
            root.put("weightedAverage", verificationScore);
        } else {
            root.putNull("weightedAverage");
        }
        root.put("riskScore", riskScore);

        return root;
    }
}