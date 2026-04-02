package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.service.CheckResultService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.DocumentType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.DocumentStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.StoredDocument;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.repository.StoredDocumentRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowLivenessRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationTenantListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStep;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service.VerificationStepService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogCommand;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.WebhookEventType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.WebhookDispatcherService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.dto.RiskScoreResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service.RiskScoreService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.r2_storage.R2StorageService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRepository repository;
    private final KycJobDispatcher kycJobDispatcher;
    private final AuditLogService auditLogService;
    private final ClientIdentityService clientIdentityService;
    private final StoredDocumentRepository storedDocumentRepository;
    private final R2StorageService r2StorageService;
    private final VerificationStepService verificationStepService;
    private final CheckResultService checkResultService;
    private final RiskScoreService riskScoreService;
    private final WebhookDispatcherService webhookDispatcherService;
    private final TenantRepository tenantRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<VerificationStatus> FLOW_ACTIVE_STATUSES = Set.of(
            VerificationStatus.INITIATED,
            VerificationStatus.IN_PROGRESS
    );

    public TableDTO getProviderVerificationsTable(int page, int size, String sortBy, String sortDir, UUID tenantId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        String sortField = sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(safePage, safeSize, dir, sortField);

        Page<Verification> result = tenantId != null
                ? repository.findAllByTenantId(tenantId, pageable)
                : repository.findAll(pageable);

        List<Column> columns = DisplayFieldScanner.getColumns(VerificationListDTO.class);
        List<VerificationListDTO> dtos = result.getContent().stream().map(this::toProviderListDto).toList();
        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    public TableDTO getTenantVerificationsTable(UUID tenantId, int page, int size, String sortBy, String sortDir, VerificationStatus status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        String sortField = sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(safePage, safeSize, dir, sortField);

        Page<Verification> result = status != null
                ? repository.findAllByTenantIdAndStatus(tenantId, status, pageable)
                : repository.findAllByTenantId(tenantId, pageable);

        List<Column> columns = DisplayFieldScanner.getColumns(VerificationTenantListDTO.class);
        List<VerificationTenantListDTO> dtos = result.getContent().stream().map(this::toTenantListDto).toList();
        return TableDTO.builder()
                .columns(columns)
                .rows(DisplayFieldScanner.getDataMaps(dtos, columns))
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    public Verification findByIdAndTenant(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Verification not found: " + id));
    }

    /**
     * Schválí verifikaci, která čeká na manuální přezkoumání.
     * Povolený přechod: REQUIRES_REVIEW → APPROVED.
     */
    @jakarta.transaction.Transactional
    public VerificationResponse approveReview(UUID id, UUID tenantId, UUID reviewerId) {
        Verification v = tenantId != null ? findByIdAndTenant(id, tenantId) : repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Verification not found: " + id));
        if (v.getStatus() != VerificationStatus.REQUIRES_REVIEW) {
            throw new IllegalStateException("verification_not_in_review");
        }
        v.setStatus(VerificationStatus.APPROVED);
        v.setCompletedAt(Instant.now());
        repository.save(v);
        try {
            auditLogService.log(new AuditLogCommand(
                    v.getTenantId(), reviewerId, AuditActorType.USER, null,
                    "VERIFICATION", v.getId().toString(),
                    "VERIFICATION_APPROVE", null, null,
                    Map.of("reviewerId", reviewerId != null ? reviewerId.toString() : "unknown"),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
        enqueueVerificationWebhook(v, WebhookEventType.VERIFICATION_APPROVED);
        return toDetailResponse(v, true);
    }

    /**
     * Zamítne verifikaci, která čeká na manuální přezkoumání.
     * Povolený přechod: REQUIRES_REVIEW → REJECTED.
     */
    @jakarta.transaction.Transactional
    public VerificationResponse rejectReview(UUID id, UUID tenantId, UUID reviewerId) {
        Verification v = tenantId != null ? findByIdAndTenant(id, tenantId) : repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Verification not found: " + id));
        if (v.getStatus() != VerificationStatus.REQUIRES_REVIEW) {
            throw new IllegalStateException("verification_not_in_review");
        }
        v.setStatus(VerificationStatus.REJECTED);
        v.setCompletedAt(Instant.now());
        repository.save(v);
        try {
            auditLogService.log(new AuditLogCommand(
                    v.getTenantId(), reviewerId, AuditActorType.USER, null,
                    "VERIFICATION", v.getId().toString(),
                    "VERIFICATION_REJECT", null, null,
                    Map.of("reviewerId", reviewerId != null ? reviewerId.toString() : "unknown"),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
        enqueueVerificationWebhook(v, WebhookEventType.VERIFICATION_REJECTED);
        return toDetailResponse(v, true);
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
     * Vyhledá verifikaci podle surového tokenu poslaného na cestě /flow/{token}.
     */
    public Verification findByToken(String rawToken) {
        String hash = hashToken(rawToken);
        return repository.findByVerificationTokenHash(hash)
                .orElseThrow(() -> new NoSuchElementException("Verification not found for token"));
    }

    @Transactional
    public boolean expireIfFlowTimedOut(Verification verification) {
        if (!FLOW_ACTIVE_STATUSES.contains(verification.getStatus())) {
            return false;
        }
        Instant expiresAt = verification.getExpiresAt();
        if (expiresAt == null || expiresAt.isAfter(Instant.now())) {
            return false;
        }
        verification.setStatus(VerificationStatus.EXPIRED);
        repository.save(verification);
        try {
            auditLogService.log(new AuditLogCommand(
                    verification.getTenantId(), null, AuditActorType.SYSTEM, null,
                    "VERIFICATION", verification.getId().toString(),
                    "VERIFICATION_EXPIRE", null, null, null,
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
        enqueueVerificationWebhook(verification, WebhookEventType.VERIFICATION_EXPIRED);
        return true;
    }

    /**
     * Vytvoří novou verifikaci a vrátí surový token (volajícímu se pošle jen jednou, neukládá se).
     *
     * @return dvojice (uložená Verification, surový token)
     */
    @Transactional
    public VerificationCreateResult create(Verification template) {
        String rawToken = generateToken();
        template.setVerificationTokenHash(hashToken(rawToken));
        template.setStatus(VerificationStatus.INITIATED);
        Verification saved = repository.save(template);
        verificationStepService.initSteps(saved);
        return new VerificationCreateResult(saved, rawToken);
    }

    /**
     * Finalizuje verifikační proces: přepne stav na READY_FOR_AUTOCHECK
     * a odešle automatické kontroly. Volá se po dokončení všech kroků sběru dat.
     */
    @Transactional
    public Verification finalizeFlow(String rawToken) {
        Verification v = findByToken(rawToken);
        if (v.getStatus() != VerificationStatus.INITIATED && v.getStatus() != VerificationStatus.IN_PROGRESS) {
            throw new IllegalStateException("verification_not_initiated");
        }
        if (v.getExpiresAt() != null && v.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("verification_expired");
        }
        v.setStatus(VerificationStatus.READY_FOR_AUTOCHECK);
        repository.save(v);
        kycJobDispatcher.dispatchChecks(v);
        return v;
    }

    public VerificationResponse toDetailResponse(Verification v, boolean includeClientIdentity) {
        String tenantName = v.getTenantId() != null
                ? tenantRepository.findById(v.getTenantId()).map(Tenant::getName).orElse(null)
                : null;
        List<VerificationStep> steps = verificationStepService.findAllByVerification(v.getId());
        List<CheckResult> checkResults = checkResultService.findAllByVerification(v.getId());
        RiskScoreResponse riskScore = riskScoreService.findByVerification(v.getId())
                .map(RiskScoreResponse::from)
                .orElse(null);
        return VerificationResponse.from(v, tenantName, steps, checkResults, riskScore, includeClientIdentity);
    }

    private VerificationListDTO toProviderListDto(Verification v) {
        String tenantName = v.getTenantId() != null
                ? tenantRepository.findById(v.getTenantId()).map(Tenant::getName).orElse(null)
                : null;
        String journeyTemplateName = v.getJourneyTemplate() != null
                ? v.getJourneyTemplate().getName()
                : null;
        ClientIdentity ci = v.getClientIdentity();
        String clientName = ci != null
                ? buildFullName(ci.getFirstName(), ci.getLastName())
                : "—";
        return VerificationListDTO.builder()
                .id(v.getId().toString())
                .clientName(clientName)
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .tenantId(v.getTenantId() != null ? v.getTenantId().toString() : null)
                .tenantName(tenantName)
                .journeyTemplateId(v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId().toString() : null)
                .journeyTemplateName(journeyTemplateName)
                .createdAt(v.getCreatedAt())
                .expiresAt(v.getExpiresAt())
                .build();
    }

    private VerificationTenantListDTO toTenantListDto(Verification v) {
        String journeyTemplateName = v.getJourneyTemplate() != null
                ? v.getJourneyTemplate().getName()
                : null;
        ClientIdentity ci = v.getClientIdentity();
        String clientName = ci != null
                ? buildFullName(ci.getFirstName(), ci.getLastName())
                : "—";
        return VerificationTenantListDTO.builder()
                .id(v.getId().toString())
                .clientName(clientName)
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .journeyTemplateId(v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId().toString() : null)
                .journeyTemplateName(journeyTemplateName)
                .createdAt(v.getCreatedAt())
                .expiresAt(v.getExpiresAt())
                .build();
    }

    // -------------------------------------------------------

    private static String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) return "—";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void savePersonalInfo(Verification v, String firstName, String lastName, String dateOfBirth) {
        markInProgressIfInitiated(v);
        if (v.getClientIdentity() == null) {
            ClientIdentity ci = ClientIdentity.builder()
                    .tenantId(v.getTenantId())
                    .firstName(firstName)
                    .lastName(lastName)
                    .dateOfBirth(dateOfBirth)
                    .build();
            ClientIdentity saved = clientIdentityService.create(ci);
            v.setClientIdentity(saved);
            repository.save(v);
        } else {
            ClientIdentity ci = v.getClientIdentity();
            ci.setFirstName(firstName);
            ci.setLastName(lastName);
            ci.setDateOfBirth(dateOfBirth);
            clientIdentityService.create(ci);
        }
        verificationStepService.completePersonalInfoStep(v);
    }

    @Transactional
    public void submitIdDocument(Verification v, DocumentType docType, UUID frontId, UUID backId) {
        markInProgressIfInitiated(v);
        String frontKey = resolveDocumentKey(frontId, v.getId());
        String backKey = backId != null ? resolveDocumentKey(backId, v.getId()) : null;

        String jobType = docType == DocumentType.PASSPORT ? "verify_passport" : "verify_czech_id";
        kycJobDispatcher.dispatchDocumentCheck(v, jobType,
                r2StorageService.createWorkerDownloadUrl(frontKey),
                backKey != null ? r2StorageService.createWorkerDownloadUrl(backKey) : null);

        repository.findById(v.getId())
                .map(Verification::getClientIdentity)
                .ifPresent(ci -> {
                    ci.setDocumentType(docType);
                    if (docType == DocumentType.CZECH_ID && ci.getNationality() == null) {
                        ci.setNationality("CZE");
                    }
                    clientIdentityService.create(ci);
                });
    }

    @Transactional
    public void submitLiveness(Verification v, List<FlowLivenessRequest.LivenessImage> images) {
        markInProgressIfInitiated(v);
        // Resolve and validate all keys upfront
        Map<UUID, String> keyByDocId = new LinkedHashMap<>();
        for (FlowLivenessRequest.LivenessImage img : images) {
            keyByDocId.put(img.getDocumentId(), resolveDocumentKey(img.getDocumentId(), v.getId()));
        }

        List<String> imagePaths = images.stream()
                .map(img -> r2StorageService.createWorkerDownloadUrl(keyByDocId.get(img.getDocumentId())))
                .toList();
        kycJobDispatcher.dispatchLiveness(v, imagePaths);

        // Use center frame as selfie for face match
        String centerKey = images.stream()
                .filter(img -> "center".equalsIgnoreCase(img.getPosition()))
                .findFirst()
                .map(img -> keyByDocId.get(img.getDocumentId()))
                .orElseThrow(() -> new IllegalArgumentException("liveness_center_image_required"));

        // Find the document front image already uploaded for this verification
        StoredDocument docFront = storedDocumentRepository
                .findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByCreatedAtDesc("VERIFICATION", v.getId(), "DOCUMENT_FRONT")
                .orElseThrow(() -> new IllegalStateException("document_front_not_found"));

        kycJobDispatcher.dispatchFaceMatch(v,
                r2StorageService.createWorkerDownloadUrl(docFront.getStorageKey()),
                r2StorageService.createWorkerDownloadUrl(centerKey));
    }

    @Transactional
    public void submitAml(Verification v, JsonNode answers) {
        markInProgressIfInitiated(v);
        verificationStepService.completeOptionalStep(v, VerificationStepType.AML_QUESTIONNAIRE, answers);
    }

    private void markInProgressIfInitiated(Verification v) {
        if (v.getStatus() != VerificationStatus.INITIATED) {
            return;
        }
        v.setStatus(VerificationStatus.IN_PROGRESS);
        repository.save(v);
        try {
            auditLogService.log(new AuditLogCommand(
                    v.getTenantId(), null, AuditActorType.SYSTEM, null,
                    "VERIFICATION", v.getId().toString(),
                    "VERIFICATION_STATUS_CHANGE", null, null,
                    Map.of("oldStatus", VerificationStatus.INITIATED.name(),
                            "newStatus", VerificationStatus.IN_PROGRESS.name()),
                    null, null, null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}
    }

    private String resolveDocumentKey(UUID documentId, UUID verificationId) {
        StoredDocument doc = storedDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document_not_found"));
        if (!"VERIFICATION".equals(doc.getOwnerType()) || !verificationId.equals(doc.getOwnerId())) {
            throw new SecurityException("document_not_owned");
        }
        if (doc.getStatus() != DocumentStatus.READY) {
            throw new IllegalArgumentException("document_not_ready");
        }
        return doc.getStorageKey();
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record VerificationCreateResult(Verification verification, String rawToken) {
    }
}
