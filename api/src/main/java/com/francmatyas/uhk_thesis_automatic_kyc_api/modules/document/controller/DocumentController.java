package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.CompleteDocumentUploadRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.DocumentDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.PresignDocumentUploadRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.PresignDocumentUploadResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/presign-upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> presignUpload(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PresignDocumentUploadRequest request
    ) {
        try {
            PresignDocumentUploadResponse res = documentService.presignUpload(request, currentUser);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{documentId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> completeUpload(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID documentId,
            @RequestBody(required = false) CompleteDocumentUploadRequest request
    ) {
        try {
            DocumentDTO res = documentService.completeUpload(documentId, request, currentUser);
            return ResponseEntity.ok(res);
        } catch (SecurityException ex) {
            if ("unauthorized".equals(ex.getMessage())) {
                return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
            }
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            if ("document_not_found".equals(ex.getMessage())) {
                return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listDocuments(
            @AuthenticationPrincipal User currentUser,
            @RequestParam String ownerType,
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String category
    ) {
        try {
            List<DocumentDTO> docs = documentService.listDocuments(ownerType, ownerId, category, currentUser);
            return ResponseEntity.ok(Map.of("documents", docs));
        } catch (SecurityException ex) {
            if ("unauthorized".equals(ex.getMessage())) {
                return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
            }
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteDocument(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID documentId
    ) {
        try {
            documentService.deleteDocument(documentId, currentUser);
            return ResponseEntity.noContent().build();
        } catch (SecurityException ex) {
            if ("unauthorized".equals(ex.getMessage())) {
                return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
            }
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }
}
