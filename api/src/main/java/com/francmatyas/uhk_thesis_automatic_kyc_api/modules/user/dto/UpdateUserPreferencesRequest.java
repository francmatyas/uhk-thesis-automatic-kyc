package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DateFormat;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DisplayMode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.NumberFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserPreferencesRequest {
    @NotBlank(message = "Language is required")
    @Size(max = 10, message = "Language code must not exceed 10 characters")
    private String language;

    @NotNull(message = "Display mode is required")
    private DisplayMode displayMode;

    @NotBlank(message = "Currency is required")
    @Size(max = 10, message = "Currency code must not exceed 10 characters")
    private String currency;

    @NotNull(message = "Date format is required")
    private DateFormat dateFormat;

    @NotNull(message = "Number format is required")
    private NumberFormat numberFormat;

    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    private String timezone;

    @NotNull(message = "24-hour time preference is required")
    private Boolean use24HourTime;
}

