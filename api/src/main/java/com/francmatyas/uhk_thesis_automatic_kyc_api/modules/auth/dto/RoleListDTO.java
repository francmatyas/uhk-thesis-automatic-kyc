package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayField;
import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "moduleDefinitions.roles.columns.name", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/p/roles/{id}")
    private String slug;

    @DisplayField(header = "moduleDefinitions.roles.columns.scope", order = 3)
    private String scope;

    @DisplayField(header = "moduleDefinitions.roles.columns.priority", order = 4)
    private int priority;
}
