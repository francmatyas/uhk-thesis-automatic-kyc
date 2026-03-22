package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.model.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {
    List<StoredDocument> findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(String ownerType, UUID ownerId);
}
