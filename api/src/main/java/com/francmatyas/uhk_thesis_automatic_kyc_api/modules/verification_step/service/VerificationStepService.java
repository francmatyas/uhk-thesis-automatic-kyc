package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResultStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.repository.CheckResultRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.WebhookDispatcherService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service.RiskScoreService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStep;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.repository.VerificationStepRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationStepService {

    private static final Set<VerificationStepType> CORE_STEPS = EnumSet.of(
            VerificationStepType.PERSONAL_INFO,
            VerificationStepType.DOC_OCR,
            VerificationStepType.FACE_MATCH,
            VerificationStepType.LIVENESS,
            VerificationStepType.AML_SCREEN
    );

    private static final Set<VerificationStatus> ADVANCEABLE = EnumSet.of(
            VerificationStatus.READY_FOR_AUTOCHECK
    );

    private static final Map<CheckType, BigDecimal> SCORE_WEIGHTS = Map.of(
            CheckType.DOC_OCR,        new BigDecimal("0.35"),
            CheckType.FACE_MATCH,     new BigDecimal("0.30"),
            CheckType.LIVENESS,       new BigDecimal("0.20"),
            CheckType.DOC_DATA_MATCH, new BigDecimal("0.15")
    );

    private final VerificationStepRepository stepRepository;
    private final CheckResultRepository checkResultRepository;
    private final VerificationRepository verificationRepository;
    private final RiskScoreService riskScoreService;
    private final WebhookDispatcherService webhookDispatcherService;

    /**
     * Vytvoří všechny kroky verifikace: základní kroky (vždy) + volitelné kroky z konfigurace šablony.
     * Má se volat jednou, hned po uložení verifikace.
     */
    @Transactional
    public void initSteps(Verification verification) {
        for (VerificationStepType type : CORE_STEPS) {
            createStep(verification, type);
        }

        JsonNode config = verification.getJourneyTemplate() != null
                ? verification.getJourneyTemplate().getConfigJson()
                : null;

        if (config != null && config.has("optionalSteps") && config.get("optionalSteps").isArray()) {
            for (JsonNode node : config.get("optionalSteps")) {
                String name = node.asText("");
                try {
                    VerificationStepType type = VerificationStepType.valueOf(name);
                    if (!CORE_STEPS.contains(type)) {
                        createStep(verification, type);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown optionalStep type '{}' in journey template {}", name,
                            verification.getJourneyTemplate().getId());
                }
            }
        }
    }

    /**
     * Označí povinný krok PERSONAL_INFO jako splněný a uloží odpovídající CheckResult.
     * Volá se hned po uložení osobních údajů klienta ve flow.
     */
    @Transactional
    public void completePersonalInfoStep(Verification verification) {
        stepRepository.findByVerificationIdAndStepType(verification.getId(), VerificationStepType.PERSONAL_INFO)
                .ifPresentOrElse(step -> {
                    step.setStatus(VerificationStepStatus.COMPLETED);
                    step.setCompletedAt(Instant.now());
                    stepRepository.save(step);
                }, () -> log.warn("PERSONAL_INFO step not found for verification {}", verification.getId()));

        CheckResult cr = new CheckResult();
        cr.setTenantId(verification.getTenantId());
        cr.setVerification(verification);
        cr.setCheckType(CheckType.PERSONAL_INFO);
        cr.setStatus(CheckResultStatus.PASSED);
        checkResultRepository.save(cr);
    }

    /**
     * Označí základní krok jako COMPLETED nebo FAILED po dokončení odpovídající úlohy workeru.
     * NEvolá tryAdvance – to dělá volající (KycResultHandler) až po uložení všech výsledků.
     */
    @Transactional
    public void markCoreStepDone(Verification verification, VerificationStepType stepType,
                                  VerificationStepStatus status) {
        stepRepository.findByVerificationIdAndStepType(verification.getId(), stepType)
                .ifPresentOrElse(step -> {
                    step.setStatus(status);
                    step.setCompletedAt(Instant.now());
                    stepRepository.save(step);
                    log.debug("Marked core step {} as {} for verification {}",
                            stepType, status, verification.getId());
                }, () -> log.warn("Core step {} not found for verification {}", stepType, verification.getId()));
    }

    /**
     * Dokončí volitelný krok, uloží CheckResult a pokusí se posunout verifikaci.
     */
    @Transactional
    public void completeOptionalStep(Verification verification, VerificationStepType stepType,
                                     JsonNode details) {
        setOptionalStepStatus(verification, stepType, VerificationStepStatus.COMPLETED, details);
        persistOptionalCheckResult(verification, stepType, CheckResultStatus.PASSED, details);
        tryAdvance(verification);
    }

    /**
     * Označí volitelný krok jako neúspěšný (např. překročen max počet OTP pokusů), uloží CheckResult a pokusí se posunout verifikaci.
     */
    @Transactional
    public void failOptionalStep(Verification verification, VerificationStepType stepType,
                                  JsonNode details) {
        setOptionalStepStatus(verification, stepType, VerificationStepStatus.FAILED, details);
        persistOptionalCheckResult(verification, stepType, CheckResultStatus.FAILED, details);
        tryAdvance(verification);
    }

    /**
     * Přeskočí volitelný krok (např. tenant se po vytvoření rozhodne jej nevyžadovat) a pokusí se posunout verifikaci.
     */
    @Transactional
    public void skipStep(Verification verification, VerificationStepType stepType) {
        setOptionalStepStatus(verification, stepType, VerificationStepStatus.SKIPPED, null);
        tryAdvance(verification);
    }

    /**
     * Vyhodnotí, zda jsou všechny kroky dokončené, a pokud ano, posune stav verifikace.
     * Je bezpečné volat opakovaně – nad stavem je idempotentní ochrana.
     */
    @Transactional
    public void tryAdvance(Verification verification) {
        if (!ADVANCEABLE.contains(verification.getStatus())) {
            return;
        }

        List<CheckResult> results = checkResultRepository.findAllByVerificationId(verification.getId());

        boolean anyError = results.stream().anyMatch(r -> r.getStatus() == CheckResultStatus.ERROR);
        // Neshoda DOC_DATA_MATCH je podnět k review, ne tvrdé selhání
        boolean anyFailed = results.stream().anyMatch(r ->
                r.getStatus() == CheckResultStatus.FAILED
                && r.getCheckType() != CheckType.DOC_DATA_MATCH);

        if (anyError || anyFailed) {
            verification.setStatus(VerificationStatus.AUTO_FAILED);
            BigDecimal failedScore = calculateOverallScore(results);
            if (failedScore != null) verification.setOverallScore(failedScore);
            verificationRepository.save(verification);
            riskScoreService.computeAndSave(verification, failedScore, results);
            enqueueVerificationWebhook(verification, WebhookEventType.VERIFICATION_FAILED);
            log.info("Verification {} advanced to AUTO_FAILED (short-circuit) score={}", verification.getId(), failedScore);
            return;
        }

        List<VerificationStep> steps = stepRepository.findAllByVerificationId(verification.getId());
        boolean allDone = steps.stream()
                .allMatch(s -> s.getStatus() != VerificationStepStatus.PENDING);
        if (!allDone) {
            return;
        }

        boolean requiresReview = results.stream().anyMatch(r ->
                r.getStatus() == CheckResultStatus.WARNING
                && (r.getCheckType() == CheckType.PEP || r.getCheckType() == CheckType.DOC_OCR))
            || results.stream().anyMatch(r ->
                (r.getStatus() == CheckResultStatus.FAILED || r.getStatus() == CheckResultStatus.WARNING)
                && r.getCheckType() == CheckType.DOC_DATA_MATCH);

        VerificationStatus newStatus;
        if (requiresReview) {
            newStatus = VerificationStatus.REQUIRES_REVIEW;
        } else {
            newStatus = VerificationStatus.AUTO_PASSED;
            verification.setCompletedAt(Instant.now());
        }

        BigDecimal overallScore = calculateOverallScore(results);
        if (overallScore != null) verification.setOverallScore(overallScore);

        verification.setStatus(newStatus);
        verificationRepository.save(verification);
        riskScoreService.computeAndSave(verification, overallScore, results);
        WebhookEventType eventType = newStatus == VerificationStatus.REQUIRES_REVIEW
                ? WebhookEventType.VERIFICATION_REQUIRES_REVIEW
                : WebhookEventType.VERIFICATION_COMPLETED;
        enqueueVerificationWebhook(verification, eventType);
        log.info("Verification {} advanced to {} score={}", verification.getId(), newStatus, overallScore);
    }

    // ------------------------------------------------------------------

    private void createStep(Verification verification, VerificationStepType type) {
        VerificationStep step = VerificationStep.builder()
                .tenantId(verification.getTenantId())
                .verification(verification)
                .stepType(type)
                .status(VerificationStepStatus.PENDING)
                .build();
        stepRepository.save(step);
    }

    private void setOptionalStepStatus(Verification verification, VerificationStepType stepType,
                                        VerificationStepStatus status, JsonNode details) {
        stepRepository.findByVerificationIdAndStepType(verification.getId(), stepType)
                .ifPresentOrElse(step -> {
                    step.setStatus(status);
                    step.setCompletedAt(Instant.now());
                    step.setDetailsJson(details);
                    stepRepository.save(step);
                }, () -> log.warn("Optional step {} not found for verification {}", stepType, verification.getId()));
    }

    private void persistOptionalCheckResult(Verification verification, VerificationStepType stepType,
                                             CheckResultStatus status, JsonNode details) {
        CheckType checkType = switch (stepType) {
            case EMAIL_VERIFICATION -> CheckType.EMAIL_VERIFICATION;
            case PHONE_VERIFICATION -> CheckType.PHONE_VERIFICATION;
            case AML_QUESTIONNAIRE -> CheckType.AML_QUESTIONNAIRE;
            default -> throw new IllegalArgumentException("Core step type has no direct CheckType mapping: " + stepType);
        };

        CheckResult cr = new CheckResult();
        cr.setTenantId(verification.getTenantId());
        cr.setVerification(verification);
        cr.setCheckType(checkType);
        cr.setStatus(status);
        cr.setDetailsJson(details);
        checkResultRepository.save(cr);
    }

    public List<VerificationStep> findAllByVerification(UUID verificationId) {
        return stepRepository.findAllByVerificationId(verificationId);
    }

    private void enqueueVerificationWebhook(Verification v, WebhookEventType eventType) {
        if (v == null || v.getTenantId() == null) return;
        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("verificationId", v.getId() != null ? v.getId().toString() : null);
            data.put("tenantId", v.getTenantId().toString());
            data.put("status", v.getStatus() != null ? v.getStatus().name() : null);
            data.put("overallScore", v.getOverallScore());
            data.put("journeyTemplateId", v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId().toString() : null);
            data.put("completedAt", v.getCompletedAt() != null ? v.getCompletedAt().toString() : null);
            data.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
            webhookDispatcherService.enqueueTenantEvent(v.getTenantId(), eventType.eventName(), data, null, null);
        } catch (Exception ex) {
            log.warn("Nepodařilo se zařadit webhook {} pro verifikaci {}", eventType.eventName(), v.getId(), ex);
        }
    }

    /**
     * Vypočítá celkové skóre verifikace jako vážený průměr dostupných skóre kontrol.
     * Zahrnuje pouze DOC_OCR (40 %), FACE_MATCH (35 %) a LIVENESS (25 %).
     * Kontroly bez skóre se přeskočí a jejich váha se redistribuuje.
     */
    private static BigDecimal calculateOverallScore(List<CheckResult> results) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (CheckResult r : results) {
            BigDecimal weight = SCORE_WEIGHTS.get(r.getCheckType());
            if (weight != null && r.getScore() != null) {
                numerator = numerator.add(weight.multiply(r.getScore()));
                denominator = denominator.add(weight);
            }
        }
        if (denominator.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
