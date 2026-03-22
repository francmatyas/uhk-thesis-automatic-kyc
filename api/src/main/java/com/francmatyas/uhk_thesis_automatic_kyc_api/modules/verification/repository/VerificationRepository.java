package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    List<Verification> findAllByTenantId(UUID tenantId);

    Page<Verification> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Verification> findAllByTenantIdAndStatus(UUID tenantId, VerificationStatus status, Pageable pageable);

    List<Verification> findAllByTenantIdAndStatus(UUID tenantId, VerificationStatus status);

    Optional<Verification> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Verification> findByVerificationTokenHash(String tokenHash);

    List<Verification> findAllByClientIdentityId(UUID clientIdentityId);
}