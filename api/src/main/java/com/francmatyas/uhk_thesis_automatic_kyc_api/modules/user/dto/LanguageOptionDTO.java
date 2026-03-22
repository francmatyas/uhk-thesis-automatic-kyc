package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class LanguageOptionDTO {
    private String label;
    private String code;
    private String flagUnicode;
}


