package com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedJsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return FieldCrypto.encrypt(MAPPER.writeValueAsString(attribute));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JsonNode for encryption", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String decrypted = FieldCrypto.decrypt(dbData);
        try {
            return MAPPER.readTree(decrypted);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize decrypted JsonNode", e);
        }
    }
}