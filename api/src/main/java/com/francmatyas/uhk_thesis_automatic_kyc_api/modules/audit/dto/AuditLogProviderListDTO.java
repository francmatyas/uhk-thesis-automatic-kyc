package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayField;
import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogProviderListDTO {

    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.event", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/p/audit-logs/{id}")
    private String action;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.timestamp", order = 3, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.actor", order = 4, type = DisplayFieldType.ENUM)
    private String actorType;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.entity", order = 5)
    private String entityType;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.result", order = 6, type = DisplayFieldType.ENUM)
    private String result;

    @DisplayField(header = "actorUserId", order = 7, hidden = true, sortable = false, filterable = false)
    private String actorUserId;

    @DisplayField(header = "moduleDefinitions.auditLogs.columns.actorUser", order = 8, type = DisplayFieldType.REFERENCE, referenceKey = "actorUserId", referenceTemplate = "/p/users/{actorUserId}", sortable = false, filterable = false)
    private String actorUserName;
}
