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
public class AuditLogListDTO {

    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "Event", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/audit-logs/{id}")
    private String action;

    @DisplayField(header = "Time", order = 3, type = DisplayFieldType.DATETIME, sortable = true, filterable = false)
    private Instant createdAt;

    @DisplayField(header = "Actor", order = 4, type = DisplayFieldType.ENUM)
    private String actorType;

    @DisplayField(header = "Entity", order = 5)
    private String entityType;

    @DisplayField(header = "Result", order = 6, type = DisplayFieldType.ENUM)
    private String result;
}
