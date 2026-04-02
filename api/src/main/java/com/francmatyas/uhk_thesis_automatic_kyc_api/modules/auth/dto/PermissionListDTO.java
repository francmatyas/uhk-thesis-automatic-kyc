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
public class PermissionListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "moduleDefinitions.permissions.columns.label", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/p/permissions/{id}")
    private String label;

    @DisplayField(header = "moduleDefinitions.permissions.columns.description", order = 3)
    private String description;
}
