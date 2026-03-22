package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private String id;
    @Email
    @Size(max = 255)
    private String email;
    @Size(max = 255)
    private String fullName;
    @Size(max = 100)
    private String givenName;
    @Size(max = 100)
    private String familyName;
    private Instant dateOfBirth;
    @Size(max = 50)
    private String gender;
    @Size(max = 10)
    private String dialCode;
    @Size(max = 30)
    private String phoneNumber;
    @Size(max = 2048)
    private String avatarUrl;
}
