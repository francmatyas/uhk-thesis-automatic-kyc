package com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Converter
public class EncryptedInstantConverter implements AttributeConverter<Instant, String> {

    // Odpovídá formátu PostgreSQL timestamp: "2025-07-17 00:00:00+02"
    private static final DateTimeFormatter POSTGRES_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        if (attribute == null) {
            return null;
        }
        return FieldCrypto.encrypt(attribute.toString());
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String decrypted = FieldCrypto.decrypt(dbData);
        try {
            // Šifrované hodnoty jsou ukládány jako ISO-8601 (např. "2025-07-17T00:00:00Z")
            return Instant.parse(decrypted);
        } catch (DateTimeParseException e) {
            // Zpětná kompatibilita: legacy nešifrovaný PostgreSQL timestamp (např. "2025-07-17 00:00:00+02")
            return OffsetDateTime.parse(decrypted, POSTGRES_TIMESTAMP).toInstant();
        }
    }
}
