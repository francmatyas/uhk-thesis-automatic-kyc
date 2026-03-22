package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
    private String id;
    @Size(max = 255)
    private String label;
    @Size(max = 500)
    private String description;
    @NotBlank
    @Size(max = 100)
    private String resource;
    @NotBlank
    @Size(max = 100)
    private String action;
    private Map<String, Object> constraintJson;
}
