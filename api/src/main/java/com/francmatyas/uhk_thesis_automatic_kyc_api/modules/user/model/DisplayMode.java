package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.EnumOptionDTO;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum DisplayMode {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    private final String label;

    DisplayMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static List<EnumOptionDTO> getOptions() {
        return Arrays.stream(DisplayMode.values())
                .map(mode -> EnumOptionDTO.builder()
                        .value(mode.name())
                        .label(mode.getLabel())
                        .build())
                .collect(Collectors.toList());
    }
}


