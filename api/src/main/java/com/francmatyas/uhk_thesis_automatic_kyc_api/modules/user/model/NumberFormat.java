package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.EnumOptionDTO;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum NumberFormat {
    /**
     * 1,234.56 (US/UK formát)
     */
    COMMA_DOT("1,234.56", ",", "."),

    /**
     * 1.234,56 (evropský formát)
     */
    DOT_COMMA("1.234,56", ".", ","),

    /**
     * 1 234.56 (mezera a tečka)
     */
    SPACE_DOT("1 234.56", " ", "."),

    /**
     * 1 234,56 (mezera a čárka)
     */
    SPACE_COMMA("1 234,56", " ", ",");

    private final String displayValue;
    private final String thousandsSeparator;
    private final String decimalSeparator;

    NumberFormat(String displayValue, String thousandsSeparator, String decimalSeparator) {
        this.displayValue = displayValue;
        this.thousandsSeparator = thousandsSeparator;
        this.decimalSeparator = decimalSeparator;
    }

    public static List<EnumOptionDTO> getOptions() {
        return Arrays.stream(NumberFormat.values())
                .map(format -> EnumOptionDTO.builder()
                        .value(format.name())
                        .label(format.getDisplayValue())
                        .build())
                .collect(Collectors.toList());
    }
}
