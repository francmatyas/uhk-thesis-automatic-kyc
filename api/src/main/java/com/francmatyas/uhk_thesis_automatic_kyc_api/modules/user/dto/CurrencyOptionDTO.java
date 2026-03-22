package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyOptionDTO {
    private String code;
    private String label;
    private String symbol;
    private String flagUnicode;
}

