package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowSendOtpRequest {
    @NotBlank
    private String contact;
    private String dialCode;
}