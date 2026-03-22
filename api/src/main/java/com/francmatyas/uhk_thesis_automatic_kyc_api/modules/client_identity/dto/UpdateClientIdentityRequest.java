package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateClientIdentityRequest {

    @Size(max = 255)
    private String firstName;

    @Size(max = 255)
    private String lastName;

    private String dateOfBirth;

    @Size(max = 3)
    private String countryOfResidence;

    private String email;

    @Size(max = 16)
    private String dialCode;

    @Size(max = 32)
    private String phone;

    @Size(max = 255)
    private String externalReference;
}