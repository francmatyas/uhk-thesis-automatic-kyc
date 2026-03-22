package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantScopedEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(
        name = "client_identities",
        indexes = {
                @Index(name = "ix_client_identities_tenant", columnList = "tenant_id"),
                @Index(name = "ix_client_identities_tenant_ext_ref", columnList = "tenant_id, external_reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE client_identities SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class ClientIdentity extends BaseEntity implements TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Vlastní referenční ID klienta na straně tenanta (volitelné). */
    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", columnDefinition = "text")
    private String firstName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", columnDefinition = "text")
    private String lastName;

    /** Datum ve formátu ISO 8601 (yyyy-MM-dd), šifrované při uložení. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "date_of_birth", columnDefinition = "text")
    private String dateOfBirth;

    /** Kód země ISO 3166-1 alpha-3, šifrovaný při uložení. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "country_of_residence", columnDefinition = "text")
    private String countryOfResidence;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String email;

    /** Telefonní předvolba ve formátu E.164, např. "+420". */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dial_code", columnDefinition = "text")
    private String dialCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String phone;

    // -------------------------------------------------------------------------
    // Pole extrahovaná z dokumentu – vyplňuje KycResultHandler po OCR
    // -------------------------------------------------------------------------

    /** Jaký typ dokumentu byl použit pro verifikaci. */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 16)
    private DocumentType documentType;

    /** Číslo dokumentu z MRZ, šifrované. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_number", columnDefinition = "text")
    private String documentNumber;

    /**
     * Datum expirace dokumentu jako řetězec YYMMDD (formát MRZ), šifrované.
     * Příklad: "301231" = 2030-12-31.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_expires_at", columnDefinition = "text")
    private String documentExpiresAt;

    /** Pohlaví dle MRZ: M / F / X, šifrované. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String sex;

    /**
     * České národní číslo (rodné číslo), např. "740812/2345".
     * Používá se také pro pole personal_number v pasu. Šifrované.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_number", columnDefinition = "text")
    private String nationalNumber;

    /**
     * ICAO alpha-3 kód vydávající země (pas: issuing_country,
     * český občanský průkaz: vždy "CZE"). Šifrované.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "issuing_country", columnDefinition = "text")
    private String issuingCountry;

    /**
     * ICAO alpha-3 kód národnosti z MRZ pasu.
     * V českém občanském průkazu není (použij issuingCountry). Šifrované.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String nationality;

    /** Místo narození načtené OCR z přední strany českého občanského průkazu. Šifrované. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "place_of_birth", columnDefinition = "text")
    private String placeOfBirth;

    /** Trvalá adresa načtená OCR ze zadní strany českého občanského průkazu. Šifrované. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String address;
}
