package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayField;
import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;
    @DisplayField(header = "moduleDefinitions.members.columns.name", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/t/{tenantSlug}/members/{id}")
    private String fullName;
    @DisplayField(header = "moduleDefinitions.members.columns.email", order = 3)
    private String email;
}
