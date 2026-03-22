package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.repository.ClientIdentityRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ClientIdentityService {

    private final ClientIdentityRepository repository;

    public List<ClientIdentity> findAllByTenant(UUID tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    public ClientIdentity findByIdAndTenant(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("ClientIdentity not found: " + id));
    }

    public Optional<ClientIdentity> findByExternalReference(UUID tenantId, String externalReference) {
        return repository.findByTenantIdAndExternalReference(tenantId, externalReference);
    }

    @Transactional
    public ClientIdentity create(ClientIdentity identity) {
        return repository.save(identity);
    }

    @Transactional
    public ClientIdentity update(UUID id, UUID tenantId, ClientIdentity patch) {
        ClientIdentity existing = findByIdAndTenant(id, tenantId);
        if (patch.getFirstName() != null) existing.setFirstName(patch.getFirstName());
        if (patch.getLastName() != null) existing.setLastName(patch.getLastName());
        if (patch.getDateOfBirth() != null) existing.setDateOfBirth(patch.getDateOfBirth());
        if (patch.getCountryOfResidence() != null) existing.setCountryOfResidence(patch.getCountryOfResidence());
        if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
        if (patch.getDialCode() != null) existing.setDialCode(patch.getDialCode());
        if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
        if (patch.getExternalReference() != null) existing.setExternalReference(patch.getExternalReference());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id, UUID tenantId) {
        ClientIdentity identity = findByIdAndTenant(id, tenantId);
        identity.markDeleted();
        repository.save(identity);
    }
}