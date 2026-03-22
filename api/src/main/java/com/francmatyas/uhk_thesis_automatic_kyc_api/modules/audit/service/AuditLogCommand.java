package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;

import java.util.Map;
import java.util.UUID;

public record AuditLogCommand(
        UUID tenantId,
        UUID actorUserId,
        AuditActorType actorType,
        UUID actorApiKeyId,
        String entityType,
        String entityId,
        String action,
        Object oldValue,
        Object newValue,
        Map<String, Object> metadata,
        String ipAddress,
        String userAgent,
        UUID correlationId,
        String requestId,
        AuditResult result,
        String errorCode
) {
}
