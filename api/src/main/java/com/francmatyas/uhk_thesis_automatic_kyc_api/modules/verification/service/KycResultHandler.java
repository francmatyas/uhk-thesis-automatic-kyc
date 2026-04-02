package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResultStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.repository.CheckResultRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.DocumentType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.repository.ClientIdentityRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStep;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogCommand;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service.VerificationStepService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zpracovává výsledky KYC worker úloh: ukládá záznamy {@link CheckResult},
 * aktualizuje stav {@link VerificationStep}
 * a deleguje posun stavu verifikace na {@link VerificationStepService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycResultHandler {

    private static final Set<String> KYC_JOB_TYPES = Set.of(
            "verify_czech_id", "verify_passport",
            "compare_faces", "liveness_check", "aml_screen"
    );

    private final VerificationRepository verificationRepository;
    private final CheckResultRepository checkResultRepository;
    private final ClientIdentityRepository clientIdentityRepository;
    private final VerificationStepService verificationStepService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public boolean supports(String jobType) {
        return KYC_JOB_TYPES.contains(jobType);
    }

    @Transactional
    public void handle(Map<String, Object> event, String workerStatus, JsonNode jobPayload) {
        String verificationIdStr = jobPayload != null && jobPayload.has("verificationId")
                ? jobPayload.get("verificationId").asText(null) : null;

        if (verificationIdStr == null) {
            log.warn("KYC result event missing verificationId in payload");
            return;
        }

        UUID verificationId = UUID.fromString(verificationIdStr);
        Verification verification = verificationRepository.findById(verificationId).orElse(null);
        if (verification == null) {
            log.warn("Verification not found for KYC result: {}", verificationId);
            return;
        }

        String jobType = (String) event.getOrDefault("jobType", "");

        switch (workerStatus) {
            case "succeeded" -> {
                if ("aml_screen".equals(jobType)) {
                    persistAmlCheckResults(verification, event);
                } else {
                    persistCheckResult(verification, jobType, event);
                }
            }
            case "failed" -> persistFailedCheck(verification, jobType, event);
            default -> { /* progress / cancelled – no check result needed */ }
        }

        verificationStepService.tryAdvance(verification);
    }

    // ------------------------------------------------------------------

    private void persistCheckResult(Verification verification, String jobType,
                                    Map<String, Object> event) {
        Object resultObj = event.get("result");
        JsonNode resultNode = resultObj != null ? objectMapper.valueToTree(resultObj) : null;

        CheckType checkType = jobTypeToCheckType(jobType);
        CheckResultStatus status = deriveStatus(checkType, jobType, resultNode);
        BigDecimal score = extractScore(checkType, resultNode);

        CheckResult cr = new CheckResult();
        cr.setTenantId(verification.getTenantId());
        cr.setVerification(verification);
        cr.setCheckType(checkType);
        cr.setStatus(status);
        cr.setScore(score);
        cr.setDetailsJson(resultNode);
        checkResultRepository.save(cr);
        log.info("Persisted CheckResult type={} status={} verificationId={}", checkType, status, verification.getId());
        try {
            auditLogService.log(new AuditLogCommand(
                    verification.getTenantId(), null, AuditActorType.SERVICE, null,
                    "CHECK_RESULT", cr.getId().toString(),
                    "CHECK_RESULT_CREATE", null, null,
                    Map.of("checkType", checkType.name(), "status", status.name(),
                            "verificationId", verification.getId().toString()),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}

        VerificationStepStatus stepStatus = status == CheckResultStatus.ERROR
                ? VerificationStepStatus.FAILED : VerificationStepStatus.COMPLETED;
        verificationStepService.markCoreStepDone(verification, jobTypeToStepType(jobType), stepStatus);

        if (checkType == CheckType.DOC_OCR && status != CheckResultStatus.ERROR) {
            persistDataMatchCheck(verification, resultNode);
            enrichClientIdentityFromOcr(verification, jobType, resultNode);
        }
    }

    /**
     * Rozdělí jeden výsledek aml_screen na samostatné řádky CheckResult pro SANCTIONS a PEP.
     */
    private void persistAmlCheckResults(Verification verification, Map<String, Object> event) {
        Object resultObj = event.get("result");
        JsonNode resultNode = resultObj != null ? objectMapper.valueToTree(resultObj) : null;
        if (resultNode == null) {
            persistAmlRow(verification, CheckType.SANCTIONS, CheckResultStatus.ERROR, null, null);
            persistAmlRow(verification, CheckType.PEP, CheckResultStatus.ERROR, null, null);
            verificationStepService.markCoreStepDone(verification, VerificationStepType.AML_SCREEN, VerificationStepStatus.FAILED);
            return;
        }

        JsonNode hits = resultNode.path("hits");

        // Rozdělení hitů podle list_type
        var sanctionHits = objectMapper.createArrayNode();
        var pepHits = objectMapper.createArrayNode();
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                String listType = hit.path("list_type").asText("");
                if ("SANCTION".equalsIgnoreCase(listType)) sanctionHits.add(hit);
                else if ("PEP".equalsIgnoreCase(listType)) pepHits.add(hit);
            }
        }

        ObjectNode sanctionDetails = objectMapper.createObjectNode();
        sanctionDetails.put("hitCount", sanctionHits.size());
        sanctionDetails.set("hits", sanctionHits);

        ObjectNode pepDetails = objectMapper.createObjectNode();
        pepDetails.put("hitCount", pepHits.size());
        pepDetails.set("hits", pepHits);

        CheckResultStatus sanctionStatus = sanctionHits.isEmpty() ? CheckResultStatus.PASSED : CheckResultStatus.FAILED;
        CheckResultStatus pepStatus = pepHits.isEmpty() ? CheckResultStatus.PASSED : CheckResultStatus.WARNING;

        persistAmlRow(verification, CheckType.SANCTIONS, sanctionStatus, null, sanctionDetails);
        persistAmlRow(verification, CheckType.PEP, pepStatus, null, pepDetails);

        verificationStepService.markCoreStepDone(verification, VerificationStepType.AML_SCREEN, VerificationStepStatus.COMPLETED);
    }

    private void persistAmlRow(Verification verification, CheckType type, CheckResultStatus status,
                                BigDecimal score, JsonNode details) {
        CheckResult cr = new CheckResult();
        cr.setTenantId(verification.getTenantId());
        cr.setVerification(verification);
        cr.setCheckType(type);
        cr.setStatus(status);
        cr.setScore(score);
        cr.setDetailsJson(details);
        checkResultRepository.save(cr);
        log.info("Persisted CheckResult type={} status={} verificationId={}", type, status, verification.getId());
        try {
            auditLogService.log(new AuditLogCommand(
                    verification.getTenantId(), null, AuditActorType.SERVICE, null,
                    "CHECK_RESULT", cr.getId().toString(),
                    "CHECK_RESULT_CREATE", null, null,
                    Map.of("checkType", type.name(), "status", status.name(),
                            "verificationId", verification.getId().toString()),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
    }

    private void persistFailedCheck(Verification verification, String jobType,
                                    Map<String, Object> event) {
        Object errorObj = event.get("error");
        JsonNode errorNode = errorObj != null ? objectMapper.valueToTree(errorObj) : null;

        if ("aml_screen".equals(jobType)) {
            // Worker selhal kompletně – označit oba AML typy kontrol jako ERROR
            persistAmlRow(verification, CheckType.SANCTIONS, CheckResultStatus.ERROR, null, errorNode);
            persistAmlRow(verification, CheckType.PEP, CheckResultStatus.ERROR, null, errorNode);
            verificationStepService.markCoreStepDone(verification, VerificationStepType.AML_SCREEN, VerificationStepStatus.FAILED);
        } else {
            CheckType checkType = jobTypeToCheckType(jobType);
            CheckResult cr = new CheckResult();
            cr.setTenantId(verification.getTenantId());
            cr.setVerification(verification);
            cr.setCheckType(checkType);
            cr.setStatus(CheckResultStatus.ERROR);
            cr.setDetailsJson(errorNode);
            checkResultRepository.save(cr);
            try {
                auditLogService.log(new AuditLogCommand(
                        verification.getTenantId(), null, AuditActorType.SERVICE, null,
                        "CHECK_RESULT", cr.getId().toString(),
                        "CHECK_RESULT_CREATE", null, null,
                        Map.of("checkType", checkType.name(), "status", CheckResultStatus.ERROR.name(),
                                "verificationId", verification.getId().toString()),
                        null, null, null, null,
                        AuditResult.SUCCESS, null
                ));
            } catch (Exception ignored) {}
            verificationStepService.markCoreStepDone(verification, jobTypeToStepType(jobType), VerificationStepStatus.FAILED);
        }
    }

    /**
     * Porovná odeslaná osobní data klienta s daty načtenými z dokumentu OCR.
     * Spouští se před enrichClientIdentityFromOcr, aby se porovnávala pouze pole skutečně odeslaná uživatelem.
     * Skóre: 1.0 = shoda, 0.7 = částečná shoda, 0.0 = neshoda; null pole jsou přeskočena.
     */
    private void persistDataMatchCheck(Verification verification, JsonNode ocrResult) {
        ClientIdentity ci = verification.getClientIdentity();
        if (ci == null || ocrResult == null) return;

        // firstName (30 %), lastName (40 %), dateOfBirth (30 %)
        BigDecimal fnScore  = compareField(ci.getFirstName(),   textOf(ocrResult, "given_names"));
        BigDecimal lnScore  = compareField(ci.getLastName(),    textOf(ocrResult, "surname"));
        BigDecimal dobScore = compareDates(ci.getDateOfBirth(), textOf(ocrResult, "date_of_birth"));

        BigDecimal numerator   = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;

        if (fnScore != null) {
            numerator   = numerator.add(new BigDecimal("0.30").multiply(fnScore));
            denominator = denominator.add(new BigDecimal("0.30"));
        }
        if (lnScore != null) {
            numerator   = numerator.add(new BigDecimal("0.40").multiply(lnScore));
            denominator = denominator.add(new BigDecimal("0.40"));
        }
        if (dobScore != null) {
            numerator   = numerator.add(new BigDecimal("0.30").multiply(dobScore));
            denominator = denominator.add(new BigDecimal("0.30"));
        }

        if (denominator.compareTo(BigDecimal.ZERO) == 0) return; // není co porovnávat

        BigDecimal score = numerator.divide(denominator, 4, RoundingMode.HALF_UP);
        CheckResultStatus status = score.compareTo(new BigDecimal("0.80")) >= 0
                ? CheckResultStatus.PASSED
                : score.compareTo(new BigDecimal("0.50")) >= 0
                        ? CheckResultStatus.WARNING
                        : CheckResultStatus.FAILED;

        ObjectNode details = objectMapper.createObjectNode();
        details.put("firstName",   fieldMatchLabel(fnScore));
        details.put("lastName",    fieldMatchLabel(lnScore));
        details.put("dateOfBirth", fieldMatchLabel(dobScore));

        CheckResult cr = new CheckResult();
        cr.setTenantId(verification.getTenantId());
        cr.setVerification(verification);
        cr.setCheckType(CheckType.DOC_DATA_MATCH);
        cr.setStatus(status);
        cr.setScore(score);
        cr.setDetailsJson(details);
        checkResultRepository.save(cr);
        log.info("Persisted DOC_DATA_MATCH status={} score={} verificationId={}", status, score, verification.getId());
    }

    /** Porovná dva řetězce po normalizaci (diakritika, velikost písmen, mezery). */
    private static BigDecimal compareField(String submitted, String document) {
        if (submitted == null || document == null) return null;
        String a = normalize(submitted);
        String b = normalize(document);
        if (a.equals(b)) return BigDecimal.ONE;
        if (a.contains(b) || b.contains(a)) return new BigDecimal("0.70");
        return BigDecimal.ZERO;
    }

    /**
     * Porovná data narození: normalizuje na 8 číslic (YYYYMMDD).
     * Podporuje ISO formát (yyyy-MM-dd) i MRZ formát (YYMMDD).
     */
    private static BigDecimal compareDates(String submitted, String document) {
        if (submitted == null || document == null) return null;
        String a = normalizeDate(submitted);
        String b = normalizeDate(document);
        if (a == null || b == null) return compareField(submitted, document);
        return a.equals(b) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    /** Odstraní diakritiku, převede na malá písmena a ořízne mezery. */
    private static String normalize(String s) {
        String decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                         .toLowerCase()
                         .replaceAll("\\s+", " ");
    }

    /** Normalizuje datum na YYYYMMDD; vrátí null pokud formát není rozpoznán. */
    private static String normalizeDate(String s) {
        if (s == null) return null;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.length() == 8) return digits; // YYYYMMDD nebo DDMMYYYY – již 8 číslic
        if (digits.length() == 6) {
            // MRZ YYMMDD → rozvinutí roku: YY < 30 → 20YY, jinak 19YY
            int yy = Integer.parseInt(digits.substring(0, 2));
            String century = yy < 30 ? "20" : "19";
            return century + digits;
        }
        return null;
    }

    private static String fieldMatchLabel(BigDecimal score) {
        if (score == null)                                return "SKIPPED";
        if (score.compareTo(BigDecimal.ONE) == 0)         return "EXACT";
        if (score.compareTo(new BigDecimal("0.70")) >= 0) return "PARTIAL";
        return "MISMATCH";
    }

    /**
     * Zapíše OCR-extrahovaná pole zpět do ClientIdentity.
     * Přepisuje pouze pole, která jsou aktuálně null – prioritu mají údaje zadané klientem.
     */
    private void enrichClientIdentityFromOcr(Verification verification, String jobType, JsonNode result) {
        if (result == null || verification.getClientIdentity() == null) return;

        ClientIdentity ci = clientIdentityRepository
                .findById(verification.getClientIdentity().getId())
                .orElse(null);
        if (ci == null) return;

        DocumentType docType = "verify_czech_id".equals(jobType) ? DocumentType.CZECH_ID : DocumentType.PASSPORT;
        if (ci.getDocumentType() == null) ci.setDocumentType(docType);

        setIfNull(ci::getDocumentNumber,    ci::setDocumentNumber,    textOf(result, "document_number"));
        setIfNull(ci::getDocumentExpiresAt, ci::setDocumentExpiresAt, textOf(result, "expiration_date"));
        setIfNull(ci::getSex,               ci::setSex,               textOf(result, "sex"));
        setIfNull(ci::getFirstName,         ci::setFirstName,         textOf(result, "given_names"));
        setIfNull(ci::getLastName,          ci::setLastName,          textOf(result, "surname"));
        setIfNull(ci::getDateOfBirth,       ci::setDateOfBirth,       textOf(result, "date_of_birth"));

        if (docType == DocumentType.CZECH_ID) {
            setIfNull(ci::getNationalNumber, ci::setNationalNumber, textOf(result, "national_number"));
            setIfNull(ci::getPlaceOfBirth,   ci::setPlaceOfBirth,   textOf(result, "place_of_birth"));
            setIfNull(ci::getAddress,        ci::setAddress,        textOf(result, "address"));
            setIfNull(ci::getIssuingCountry, ci::setIssuingCountry, "CZE");
            setIfNull(ci::getNationality,    ci::setNationality,    "CZE");
        } else {
            setIfNull(ci::getNationalNumber, ci::setNationalNumber, textOf(result, "personal_number"));
            setIfNull(ci::getIssuingCountry, ci::setIssuingCountry, textOf(result, "issuing_country"));
            setIfNull(ci::getNationality,    ci::setNationality,    textOf(result, "nationality"));
        }

        clientIdentityRepository.save(ci);
        log.info("Enriched ClientIdentity {} from {} OCR", ci.getId(), docType);
        try {
            auditLogService.log(new AuditLogCommand(
                    verification.getTenantId(), null, AuditActorType.SERVICE, null,
                    "CLIENT_IDENTITY", ci.getId().toString(),
                    "CLIENT_IDENTITY_ENRICHED", null, null,
                    Map.of("documentType", docType.name(), "verificationId", verification.getId().toString()),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------

    private static CheckType jobTypeToCheckType(String jobType) {
        return switch (jobType) {
            case "verify_czech_id", "verify_passport" -> CheckType.DOC_OCR;
            case "compare_faces" -> CheckType.FACE_MATCH;
            case "liveness_check" -> CheckType.LIVENESS;
            default -> CheckType.DOC_OCR;
        };
    }

    private static VerificationStepType jobTypeToStepType(String jobType) {
        return switch (jobType) {
            case "verify_czech_id", "verify_passport" -> VerificationStepType.DOC_OCR;
            case "compare_faces" -> VerificationStepType.FACE_MATCH;
            case "liveness_check" -> VerificationStepType.LIVENESS;
            default -> VerificationStepType.DOC_OCR;
        };
    }

    private static CheckResultStatus deriveStatus(CheckType type, String jobType, JsonNode result) {
        if (result == null) return CheckResultStatus.ERROR;
        return switch (type) {
            case DOC_OCR -> {
                // confidence: "high" | "medium" | "low"
                String confidence = result.path("confidence").asText("low");
                yield switch (confidence) {
                    case "high" -> CheckResultStatus.PASSED;
                    // Czech ID: medium je akceptovatelné (kompozitní checksum často selhává u moderních karet)
                    // Passport: medium znamená selhání composite/personal number checksumu -> označit pro review
                    case "medium" -> "verify_czech_id".equals(jobType)
                            ? CheckResultStatus.PASSED
                            : CheckResultStatus.WARNING;
                    default -> CheckResultStatus.FAILED; // "low" – basic checksums failed
                };
            }
            case FACE_MATCH -> {
                // compare_faces vrací {"match": bool, "confidence": float}
                JsonNode match = result.path("match");
                yield !match.isMissingNode() && match.asBoolean()
                        ? CheckResultStatus.PASSED : CheckResultStatus.FAILED;
            }
            case LIVENESS -> {
                // liveness_check vrací {"is_alive": bool, "confidence": float}
                JsonNode live = result.path("is_alive");
                yield !live.isMissingNode() && live.asBoolean()
                        ? CheckResultStatus.PASSED : CheckResultStatus.FAILED;
            }
            default -> CheckResultStatus.PASSED;
        };
    }

    private static BigDecimal extractScore(CheckType type, JsonNode result) {
        if (result == null) return null;
        return switch (type) {
            case FACE_MATCH -> nodeToDecimal(result.path("confidence"));
            case LIVENESS -> nodeToDecimal(result.path("confidence"));
            case DOC_OCR -> extractOcrScore(result);
            default -> null;
        };
    }

    /**
     * Číselné skóre OCR: bere valid_score z PassportEye (0–100), normalizuje
     * na rozsah 0–1 a odečítá penalizace za každou položku v confidence_notes.
     * Pokud valid_score chybí, mapuje textové confidence na číslo.
     */
    private static BigDecimal extractOcrScore(JsonNode result) {
        BigDecimal base;
        JsonNode validScoreNode = result.path("raw").path("valid_score");
        if (!validScoreNode.isMissingNode() && !validScoreNode.isNull()) {
            BigDecimal raw = nodeToDecimal(validScoreNode);
            base = raw != null
                    ? raw.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    : ocrConfidenceToScore(result);
        } else {
            base = ocrConfidenceToScore(result);
        }
        JsonNode notes = result.path("confidence_notes");
        if (notes.isArray()) {
            for (JsonNode note : notes) {
                base = base.subtract(ocrNotePenalty(note.asText("")));
            }
        }
        return base.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private static BigDecimal ocrConfidenceToScore(JsonNode result) {
        return switch (result.path("confidence").asText("low")) {
            case "high"   -> new BigDecimal("0.90");
            case "medium" -> new BigDecimal("0.70");
            default       -> new BigDecimal("0.30");
        };
    }

    private static BigDecimal ocrNotePenalty(String note) {
        return switch (note) {
            case "UNEXPECTED_DOC_TYPE"                          -> new BigDecimal("0.15");
            case "CHECKSUM_NUMBER_FAILED",
                 "CHECKSUM_DOB_FAILED",
                 "CHECKSUM_EXPIRY_FAILED"                      -> new BigDecimal("0.08");
            case "CHECKSUM_PERSONAL_FAILED"                    -> new BigDecimal("0.05");
            case "CHECKSUM_COMPOSITE_FAILED"                   -> new BigDecimal("0.03");
            default                                            -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal nodeToDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static <T> void setIfNull(java.util.function.Supplier<T> getter,
                                      java.util.function.Consumer<T> setter, T value) {
        if (getter.get() == null && value != null) setter.accept(value);
    }
}