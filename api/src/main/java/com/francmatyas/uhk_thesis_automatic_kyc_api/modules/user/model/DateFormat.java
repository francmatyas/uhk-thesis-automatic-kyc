package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.EnumOptionDTO;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum DateFormat {
    /**
     * ISO formát: 2024-12-31
     */
    Y_M_D("Y-m-d", "yyyy-MM-dd", "2024-12-31"),

    /**
     * Evropský formát: 31. 12. 2024
     */
    D_M_Y("d. m. Y", "dd. MM. yyyy", "31. 12. 2024"),

    /**
     * US formát: 12/31/2024
     */
    M_D_Y("m/d/Y", "MM/dd/yyyy", "12/31/2024"),

    /**
     * Evropský formát s lomítky: 31/12/2024
     */
    D_M_Y_SLASH("d/m/Y", "dd/MM/yyyy", "31/12/2024");

    private final String displayValue;
    private final String javaPattern;
    private final String example;

    DateFormat(String displayValue, String javaPattern, String example) {
        this.displayValue = displayValue;
        this.javaPattern = javaPattern;
        this.example = example;
    }

    public static List<EnumOptionDTO> getOptions() {
        return Arrays.stream(DateFormat.values())
                .map(format -> EnumOptionDTO.builder()
                        .value(format.name())
                        .label(format.getExample())
                        .build())
                .collect(Collectors.toList());
    }
}
