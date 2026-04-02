package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.CompleteDocumentUploadRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.DocumentDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.PresignDocumentUploadRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.PresignDocumentUploadResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.DocumentKind;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.DocumentStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.StoredDocument;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.repository.StoredDocumentRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.WebhookDispatcherService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.r2_storage.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Duration DEFAULT_UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Set<String> PUBLIC_CATEGORIES = Set.of("AVATAR");

    private final StoredDocumentRepository documentRepository;
    private final R2StorageService storageService;
    private final WebhookDispatcherService webhookDispatcherService;

    @Transactional
    public PresignDocumentUploadResponse presignUpload(PresignDocumentUploadRequest request, User currentUser) {
        String ownerType = normalizeOwnerType(request.getOwnerType());
        authorizeOwnerAccess(currentUser, ownerType, request.getOwnerId());
        String category = normalizeCategory(request.getCategory());
        String originalFilename = sanitizeFilename(request.getFilename());
        String key = buildStorageKey(ownerType, request.getOwnerId(), category, originalFilename);
        Instant expiresAt = Instant.now().plus(DEFAULT_UPLOAD_TTL);

        StoredDocument doc = new StoredDocument();
        doc.setOwnerType(ownerType);
        doc.setOwnerId(request.getOwnerId());
        doc.setTenantId(request.getTenantId());
        doc.setCategory(category);
        doc.setKind(DocumentKind.UPLOADED);
        doc.setStatus(DocumentStatus.PENDING_UPLOAD);
        doc.setStorageKey(key);
        doc.setOriginalFilename(originalFilename);
        doc.setContentType(request.getContentType().trim().toLowerCase(Locale.ROOT));
        doc.setUploadExpiresAt(expiresAt);
        doc.setUploadedBy(currentUser);
        StoredDocument saved = documentRepository.save(doc);

        String uploadUrl = storageService.createUploadUrl(key, doc.getContentType(), DEFAULT_UPLOAD_TTL);
        return PresignDocumentUploadResponse.builder()
                .documentId(saved.getId())
                .uploadUrl(uploadUrl)
                .storageKey(key)
                .publicUrl(storageService.publicUrlForKey(key))
                .uploadExpiresAt(expiresAt)
                .build();
    }

    @Transactional
    public DocumentDTO completeUpload(UUID documentId, CompleteDocumentUploadRequest request, User currentUser) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document_not_found"));
        authorizeOwnerAccess(currentUser, doc.getOwnerType(), doc.getOwnerId());

        if (doc.getStatus() == DocumentStatus.DELETED) {
            throw new IllegalArgumentException("document_deleted");
        }

        doc.setStatus(DocumentStatus.READY);
        doc.setSizeBytes(request != null ? request.getSizeBytes() : null);
        doc.setChecksum(request != null ? trimToNull(request.getChecksum()) : null);
        doc.setUploadExpiresAt(null);
        StoredDocument saved = documentRepository.save(doc);
        enqueueDocumentWebhook(saved, "document.ready");
        return toDto(saved);
    }

    @Transactional
    public PresignDocumentUploadResponse presignFlowUpload(UUID verificationId, UUID tenantId,
                                                           String filename, String contentType, String category) {
        String normalizedCategory = normalizeCategory(category);
        String originalFilename = sanitizeFilename(filename);
        String key = buildStorageKey("VERIFICATION", verificationId, normalizedCategory, originalFilename);
        Instant expiresAt = Instant.now().plus(DEFAULT_UPLOAD_TTL);

        StoredDocument doc = new StoredDocument();
        doc.setOwnerType("VERIFICATION");
        doc.setOwnerId(verificationId);
        doc.setTenantId(tenantId);
        doc.setCategory(normalizedCategory);
        doc.setKind(DocumentKind.UPLOADED);
        doc.setStatus(DocumentStatus.PENDING_UPLOAD);
        doc.setStorageKey(key);
        doc.setOriginalFilename(originalFilename);
        doc.setContentType(contentType.trim().toLowerCase(Locale.ROOT));
        doc.setUploadExpiresAt(expiresAt);
        StoredDocument saved = documentRepository.save(doc);

        String uploadUrl = storageService.createUploadUrl(key, doc.getContentType(), DEFAULT_UPLOAD_TTL);
        return PresignDocumentUploadResponse.builder()
                .documentId(saved.getId())
                .uploadUrl(uploadUrl)
                .storageKey(key)
                .publicUrl(storageService.publicUrlForKey(key))
                .uploadExpiresAt(expiresAt)
                .build();
    }

    @Transactional
    public DocumentDTO completeFlowUpload(UUID documentId, UUID verificationId, CompleteDocumentUploadRequest request) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document_not_found"));

        if (!"VERIFICATION".equals(doc.getOwnerType()) || !verificationId.equals(doc.getOwnerId())) {
            throw new SecurityException("forbidden");
        }
        if (doc.getStatus() == DocumentStatus.DELETED) {
            throw new IllegalArgumentException("document_deleted");
        }

        doc.setStatus(DocumentStatus.READY);
        doc.setSizeBytes(request != null ? request.getSizeBytes() : null);
        doc.setChecksum(request != null ? trimToNull(request.getChecksum()) : null);
        doc.setUploadExpiresAt(null);
        StoredDocument saved = documentRepository.save(doc);
        enqueueDocumentWebhook(saved, "document.ready");
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> listDocuments(String ownerType, UUID ownerId, String category, User currentUser) {
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        authorizeOwnerAccess(currentUser, normalizedOwnerType, ownerId);
        String normalizedCategory = trimToNull(category);
        return documentRepository.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(normalizedOwnerType, ownerId).stream()
                .filter(doc -> normalizedCategory == null
                        || normalizedCategory.equalsIgnoreCase(doc.getCategory()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID documentId, User currentUser) {
        StoredDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document_not_found"));
        authorizeOwnerAccess(currentUser, doc.getOwnerType(), doc.getOwnerId());

        doc.setStatus(DocumentStatus.DELETED);
        documentRepository.save(doc);
        enqueueDocumentWebhook(doc, "document.deleted");
        documentRepository.delete(doc);

        try {
            storageService.delete(doc.getStorageKey());
        } catch (Exception ignored) {
            // Smazání metadat zůstává autoritativní i při selhání úklidu ve storage.
        }
    }

    private DocumentDTO toDto(StoredDocument doc) {
        return DocumentDTO.builder()
                .id(doc.getId())
                .ownerType(doc.getOwnerType())
                .ownerId(doc.getOwnerId())
                .tenantId(doc.getTenantId())
                .category(doc.getCategory())
                .kind(doc.getKind())
                .status(doc.getStatus())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .sizeBytes(doc.getSizeBytes())
                .checksum(doc.getChecksum())
                .storageKey(doc.getStorageKey())
                .publicUrl(storageService.publicUrlForKey(doc.getStorageKey()))
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private String normalizeOwnerType(String ownerType) {
        String normalized = trimToNull(ownerType);
        if (normalized == null) {
            throw new IllegalArgumentException("owner_type_required");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        String normalized = trimToNull(category);
        if (normalized == null) {
            throw new IllegalArgumentException("category_required");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        String input = trimToNull(filename);
        if (input == null) {
            throw new IllegalArgumentException("filename_required");
        }
        String noPath = input.replace("\\", "/");
        int slashIdx = noPath.lastIndexOf('/');
        String base = slashIdx >= 0 ? noPath.substring(slashIdx + 1) : noPath;
        if (base.isBlank()) {
            throw new IllegalArgumentException("filename_required");
        }
        return base;
    }

    private String buildStorageKey(String ownerType, UUID ownerId, String category, String filename) {
        String extension = "";
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < filename.length() - 1) {
            extension = filename.substring(dotIdx).toLowerCase(Locale.ROOT);
        }
        String prefix = PUBLIC_CATEGORIES.contains(category) ? "public" : "private";
        return prefix
                + "/documents/"
                + ownerType.toLowerCase(Locale.ROOT)
                + "/"
                + ownerId
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void authorizeOwnerAccess(User currentUser, String ownerType, UUID ownerId) {
        if (currentUser == null) {
            throw new SecurityException("unauthorized");
        }

        if ("USER".equalsIgnoreCase(ownerType)) {
            if (currentUser.isProviderUser()) {
                return;
            }
            if (ownerId != null && ownerId.equals(currentUser.getId())) {
                return;
            }
            throw new SecurityException("forbidden");
        }

        if (!currentUser.isProviderUser()) {
            throw new SecurityException("forbidden");
        }
    }

    private void enqueueDocumentWebhook(StoredDocument doc, String eventType) {
        if (doc == null || doc.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("documentId", doc.getId() != null ? doc.getId().toString() : null);
            data.put("ownerType", doc.getOwnerType());
            data.put("ownerId", doc.getOwnerId() != null ? doc.getOwnerId().toString() : null);
            data.put("tenantId", doc.getTenantId().toString());
            data.put("category", doc.getCategory());
            data.put("kind", doc.getKind() != null ? doc.getKind().name() : null);
            data.put("status", doc.getStatus() != null ? doc.getStatus().name() : null);
            data.put("storageKey", doc.getStorageKey());
            data.put("contentType", doc.getContentType());
            data.put("originalFilename", doc.getOriginalFilename());
            data.put("sizeBytes", doc.getSizeBytes());
            data.put("checksum", doc.getChecksum());
            data.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
            data.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);
            data.put("publicUrl", storageService.publicUrlForKey(doc.getStorageKey()));

            webhookDispatcherService.enqueueTenantEvent(doc.getTenantId(), eventType, data, null, null);
        } catch (Exception ex) {
            log.warn("Failed to enqueue webhook event {} for document {}", eventType, doc.getId(), ex);
        }
    }
}
