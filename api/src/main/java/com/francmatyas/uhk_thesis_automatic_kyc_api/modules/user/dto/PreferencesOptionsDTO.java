package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreferencesOptionsDTO {
    private List<LanguageOptionDTO> languages;
    private List<CurrencyOptionDTO> currencies;
    private List<TimezoneOptionDTO> timezones;
    private List<EnumOptionDTO> displayModes;
    private List<EnumOptionDTO> dateFormats;
    private List<EnumOptionDTO> numberFormats;
}

