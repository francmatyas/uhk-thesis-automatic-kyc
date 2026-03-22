package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record ClientIdentityResponse(
        UUID id,
        UUID tenantId,
        String externalReference,
        // Uvedeno klientem
        String firstName,
        String lastName,
        String dateOfBirth,
        String countryOfResidence,
        String email,
        String dialCode,
        String phone,
        // Extrahováno z dokumentu
        DocumentType documentType,
        String documentNumber,
        String documentExpiresAt,
        String sex,
        String nationalNumber,
        String issuingCountry,
        String nationality,
        String placeOfBirth,
        String address,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientIdentityResponse from(ClientIdentity c) {
        return new ClientIdentityResponse(
                c.getId(), c.getTenantId(), c.getExternalReference(),
                c.getFirstName(), c.getLastName(), c.getDateOfBirth(),
                c.getCountryOfResidence(), c.getEmail(), c.getDialCode(), c.getPhone(),
                c.getDocumentType(), c.getDocumentNumber(), c.getDocumentExpiresAt(),
                c.getSex(), c.getNationalNumber(), c.getIssuingCountry(),
                c.getNationality(), c.getPlaceOfBirth(), c.getAddress(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
