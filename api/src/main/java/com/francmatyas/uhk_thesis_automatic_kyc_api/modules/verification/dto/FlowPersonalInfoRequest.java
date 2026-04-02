package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowPersonalInfoRequest {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    @NotBlank
    private String dateOfBirth;
}