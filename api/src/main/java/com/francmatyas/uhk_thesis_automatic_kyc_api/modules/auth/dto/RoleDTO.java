package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private String id;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 100)
    private String slug;
    private RoleScope scope;
    @Size(max = 500)
    private String description;
    private int priority;

    /** Pokud je uvedeno při vytvoření/úpravě, nahradí tímto setem oprávnění role. */
    @Size(max = 200)
    private List<String> permissionIds;
}
