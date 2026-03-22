package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FlowSubmitRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /** Formát ISO 8601 (yyyy-MM-dd). */
    @NotBlank
    private String dateOfBirth;

    /** ISO 3166-1 alpha-3 kód. */
    @Size(max = 3)
    private String countryOfResidence;

    private String email;

    @Size(max = 16)
    private String dialCode;

    @Size(max = 32)
    private String phone;
}
