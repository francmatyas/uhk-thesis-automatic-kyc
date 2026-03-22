package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKey;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByTenantId(UUID tenantId);
    List<ApiKey> findByTenantIdAndStatus(UUID tenantId, ApiKeyStatus status);
    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ApiKey> findByPublicKey(String publicKey);
    Optional<ApiKey> findByPublicKeyAndStatus(String publicKey, ApiKeyStatus status);

    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :lastUsedAt where k.id = :id")
    int touchLastUsedAt(@Param("id") UUID id, @Param("lastUsedAt") Instant lastUsedAt);
}
