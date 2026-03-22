package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record AuditLogDetailDTO(
        UUID id,
        Instant createdAt,
        UUID tenantId,
        String actorType,
        String entityType,
        String action,
        String result,
        String errorCode
) {}
