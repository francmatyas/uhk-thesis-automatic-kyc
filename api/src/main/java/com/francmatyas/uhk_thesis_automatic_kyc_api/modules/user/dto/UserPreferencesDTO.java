package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DateFormat;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DisplayMode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.NumberFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPreferencesDTO {
    private String id;
    private String language;
    private DisplayMode displayMode;
    private String currency;
    private DateFormat dateFormat;
    private NumberFormat numberFormat;
    private String timezone;
    private boolean use24HourTime;
}


