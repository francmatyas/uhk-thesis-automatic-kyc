package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationTenantListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationRepository repository;
    private final KycJobDispatcher kycJobDispatcher;
    private final ClientIdentityService clientIdentityService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public TableDTO getProviderVerificationsTable(int page, int size, String sortBy, String sortDir, UUID tenantId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
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
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
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
     * Vyhledá verifikaci podle surového tokenu poslaného na cestě /flow/{token}.
     */
    public Verification findByToken(String rawToken) {
        String hash = hashToken(rawToken);
        return repository.findByVerificationTokenHash(hash)
                .orElseThrow(() -> new NoSuchElementException("Verification not found for token"));
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
        return new VerificationCreateResult(saved, rawToken);
    }

    @Transactional
    public Verification transition(UUID id, UUID tenantId, VerificationStatus newStatus) {
        Verification v = findByIdAndTenant(id, tenantId);
        v.setStatus(newStatus);
        if (isTerminal(newStatus)) {
            v.setCompletedAt(Instant.now());
        }
        return repository.save(v);
    }

    /**
     * Volá se, když klient odešle formulář verifikačního procesu.
     * Vytvoří a propojí ClientIdentity, přepne stav na READY_FOR_AUTOCHECK
     * a odešle automatické kontroly.
     */
    @Transactional
    public Verification submitFlow(String rawToken, ClientIdentity pii) {
        Verification v = findByToken(rawToken);
        if (v.getStatus() != VerificationStatus.INITIATED) {
            throw new IllegalStateException("Verification is not in INITIATED state");
        }
        if (v.getExpiresAt() != null && v.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Verification has expired");
        }
        pii.setTenantId(v.getTenantId());
        ClientIdentity saved = clientIdentityService.create(pii);
        v.setClientIdentity(saved);
        v.setStatus(VerificationStatus.READY_FOR_AUTOCHECK);
        repository.save(v);
        kycJobDispatcher.dispatchChecks(v);
        return v;
    }

    /**
     * Odeslání všech automatických kontrol verifikace přes RabbitMQ.
     * Přepne verifikaci do stavu IN_PROGRESS.
     */
    @Transactional
    public Verification dispatchAutoChecks(UUID id, UUID tenantId) {
        Verification v = findByIdAndTenant(id, tenantId);
        v.setStatus(VerificationStatus.IN_PROGRESS);
        repository.save(v);
        kycJobDispatcher.dispatchChecks(v);
        return v;
    }

    private VerificationListDTO toProviderListDto(Verification v) {
        return VerificationListDTO.builder()
                .id(v.getId().toString())
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .tenantId(v.getTenantId() != null ? v.getTenantId().toString() : null)
                .journeyTemplateId(v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId().toString() : null)
                .createdAt(v.getCreatedAt())
                .expiresAt(v.getExpiresAt())
                .build();
    }

    private VerificationTenantListDTO toTenantListDto(Verification v) {
        return VerificationTenantListDTO.builder()
                .id(v.getId().toString())
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .journeyTemplateId(v.getJourneyTemplate() != null ? v.getJourneyTemplate().getId().toString() : null)
                .createdAt(v.getCreatedAt())
                .expiresAt(v.getExpiresAt())
                .build();
    }

    // -------------------------------------------------------

    private static boolean isTerminal(VerificationStatus s) {
        return s == VerificationStatus.APPROVED
                || s == VerificationStatus.REJECTED
                || s == VerificationStatus.EXPIRED;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
