package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientIdentityRepository extends JpaRepository<ClientIdentity, UUID> {

    List<ClientIdentity> findAllByTenantId(UUID tenantId);

    Optional<ClientIdentity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ClientIdentity> findByTenantIdAndExternalReference(UUID tenantId, String externalReference);
}