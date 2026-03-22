package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimezoneOptionDTO {
    private String value;
    private String label;
    private String offset;
}

