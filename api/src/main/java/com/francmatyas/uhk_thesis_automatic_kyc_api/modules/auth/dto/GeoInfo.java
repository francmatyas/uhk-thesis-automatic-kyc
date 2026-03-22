package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GeoInfo {
    private String ip;
    private String countryCode;
    private String countryName;
    private String city;
    private Double latitude;
    private Double longitude;
}
