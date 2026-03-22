package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto;

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
public class TenantListDTO {
    @DisplayField(header = "Id", order = 1, hidden = true, sortable = false, filterable = false)
    private String id;

    @DisplayField(header = "Name", order = 2, type = DisplayFieldType.REFERENCE, referenceKey = "id", referenceTemplate = "/p/tenants/{id}")
    private String name;

    @DisplayField(header = "Slug", order = 3)
    private String slug;

    @DisplayField(header = "Status", order = 4)
    private String status;
}
