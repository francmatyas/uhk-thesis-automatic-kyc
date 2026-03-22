package com.francmatyas.uhk_thesis_automatic_kyc_api.config;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserAgentParserConfig {

    @Bean
    public UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer.newBuilder()
                .withField("DeviceClass")
                .withField("DeviceBrand")
                .withField("DeviceName")
                .withField("OperatingSystemName")
                .withField("OperatingSystemVersionMajor")
                .withField("AgentName")
                .withField("AgentVersionMajor")
                .withField("DeviceCpu")
                .immediateInitialization()
                .build();
    }
}
