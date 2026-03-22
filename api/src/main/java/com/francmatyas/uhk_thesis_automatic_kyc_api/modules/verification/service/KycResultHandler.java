package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResultStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.repository.CheckResultRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.DocumentType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.repository.ClientIdentityRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zpracovává výsledky KYC worker úloh: ukládá záznamy {@link CheckResult}
 * a posouvá stav {@link Verification}, jakmile jsou všechny kontroly dokončené.
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
    private final ObjectMapper objectMapper;

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

        advanceVerificationStatus(verification);
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

        // Obohacení ClientIdentity o OCR pole při úspěšném načtení dokumentu
        if (checkType == CheckType.DOC_OCR && status != CheckResultStatus.ERROR) {
            enrichClientIdentityFromOcr(verification, jobType, resultNode);
        }
    }

    /**
     * Zapíše OCR-extrahovaná pole zpět do ClientIdentity, aby byl obohacený profil
     * dostupný pro risk scoring a manuální kontrolu.
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

        setIfNull(ci::getDocumentNumber,   ci::setDocumentNumber,   textOf(result, "document_number"));
        setIfNull(ci::getDocumentExpiresAt, ci::setDocumentExpiresAt, textOf(result, "expiration_date"));
        setIfNull(ci::getSex,              ci::setSex,              textOf(result, "sex"));

        // surname / given_names z MRZ přepisovat jen pokud ještě nejsou vyplněné
        setIfNull(ci::getFirstName,  ci::setFirstName,  textOf(result, "given_names"));
        setIfNull(ci::getLastName,   ci::setLastName,   textOf(result, "surname"));
        setIfNull(ci::getDateOfBirth, ci::setDateOfBirth, textOf(result, "date_of_birth"));

        if (docType == DocumentType.CZECH_ID) {
            setIfNull(ci::getNationalNumber, ci::setNationalNumber, textOf(result, "national_number"));
            setIfNull(ci::getPlaceOfBirth,   ci::setPlaceOfBirth,   textOf(result, "place_of_birth"));
            setIfNull(ci::getAddress,        ci::setAddress,        textOf(result, "address"));
            setIfNull(ci::getIssuingCountry, ci::setIssuingCountry, "CZE");
        } else {
            setIfNull(ci::getNationalNumber, ci::setNationalNumber, textOf(result, "personal_number"));
            setIfNull(ci::getIssuingCountry, ci::setIssuingCountry, textOf(result, "issuing_country"));
            setIfNull(ci::getNationality,    ci::setNationality,    textOf(result, "nationality"));
        }

        clientIdentityRepository.save(ci);
        log.info("Enriched ClientIdentity {} from {} OCR", ci.getId(), docType);
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

    /**
     * Rozdělí jeden výsledek aml_screen na samostatné řádky CheckResult pro SANCTIONS a PEP.
     * Worker vrací všechny zásahy v jednom seznamu; každý nese list_type "SANCTION" nebo "PEP".
     */
    private void persistAmlCheckResults(Verification verification, Map<String, Object> event) {
        Object resultObj = event.get("result");
        JsonNode resultNode = resultObj != null ? objectMapper.valueToTree(resultObj) : null;
        if (resultNode == null) {
            persistAmlRow(verification, CheckType.SANCTIONS, CheckResultStatus.ERROR, null, null);
            persistAmlRow(verification, CheckType.PEP, CheckResultStatus.ERROR, null, null);
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
    }

    private void persistFailedCheck(Verification verification, String jobType,
                                    Map<String, Object> event) {
        Object errorObj = event.get("error");
        JsonNode errorNode = errorObj != null ? objectMapper.valueToTree(errorObj) : null;

        if ("aml_screen".equals(jobType)) {
            // Worker selhal kompletně – označit oba AML typy kontrol jako ERROR
            persistAmlRow(verification, CheckType.SANCTIONS, CheckResultStatus.ERROR, null, errorNode);
            persistAmlRow(verification, CheckType.PEP, CheckResultStatus.ERROR, null, errorNode);
        } else {
            CheckType checkType = jobTypeToCheckType(jobType);
            CheckResult cr = new CheckResult();
            cr.setTenantId(verification.getTenantId());
            cr.setVerification(verification);
            cr.setCheckType(checkType);
            cr.setStatus(CheckResultStatus.ERROR);
            cr.setDetailsJson(errorNode);
            checkResultRepository.save(cr);
        }
    }

    /**
     * Po uložení každého výsledku kontroly znovu vyhodnotí celkový stav verifikace.
     * Pokud mají všechny očekávané kontroly výsledek → AUTO_PASSED nebo AUTO_FAILED.
     * Pokud některá kontrola ještě chybí → zůstává IN_PROGRESS.
     */
    private void advanceVerificationStatus(Verification verification) {
        List<CheckResult> results = checkResultRepository.findAllByVerificationId(verification.getId());

        boolean anyError = results.stream()
                .anyMatch(r -> r.getStatus() == CheckResultStatus.ERROR);
        boolean anyFailed = results.stream()
                .anyMatch(r -> r.getStatus() == CheckResultStatus.FAILED);

        boolean hasSanctions = results.stream().anyMatch(r -> r.getCheckType() == CheckType.SANCTIONS);
        boolean hasPep = results.stream().anyMatch(r -> r.getCheckType() == CheckType.PEP);
        boolean hasDoc = results.stream().anyMatch(r -> r.getCheckType() == CheckType.DOC_OCR);

        if (!hasSanctions || !hasPep || !hasDoc) {
            // Kontroly ještě běží – zatím není co dělat
            return;
        }

        // WARNING případy vyžadující lidskou kontrolu místo automatického zamítnutí
        boolean requiresReview = results.stream().anyMatch(r ->
                r.getStatus() == CheckResultStatus.WARNING
                && (r.getCheckType() == CheckType.PEP || r.getCheckType() == CheckType.DOC_OCR));

        if (anyError || anyFailed) {
            verification.setStatus(VerificationStatus.AUTO_FAILED);
        } else if (requiresReview) {
            verification.setStatus(VerificationStatus.REQUIRES_REVIEW);
        } else {
            verification.setStatus(VerificationStatus.AUTO_PASSED);
        }
        verificationRepository.save(verification);
        log.info("Verification {} advanced to {}", verification.getId(), verification.getStatus());
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
                // compare_faces vrací {"match": bool, "similarity": float}
                JsonNode match = result.path("match");
                yield !match.isMissingNode() && match.asBoolean()
                        ? CheckResultStatus.PASSED : CheckResultStatus.FAILED;
            }
            case LIVENESS -> {
                // liveness_check vrací {"is_live": bool, "score": float}
                JsonNode live = result.path("is_live");
                yield !live.isMissingNode() && live.asBoolean()
                        ? CheckResultStatus.PASSED : CheckResultStatus.FAILED;
            }
            default -> CheckResultStatus.PASSED;
        };
    }

    private static BigDecimal extractScore(CheckType type, JsonNode result) {
        if (result == null) return null;
        return switch (type) {
            case FACE_MATCH -> nodeToDecimal(result.path("similarity"));
            case LIVENESS -> nodeToDecimal(result.path("score"));
            default -> null;
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
}
