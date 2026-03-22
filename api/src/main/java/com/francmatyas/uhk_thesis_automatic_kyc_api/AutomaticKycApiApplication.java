package com.francmatyas.uhk_thesis_automatic_kyc_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.AppProps;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
@EnableScheduling
public class AutomaticKycApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomaticKycApiApplication.class, args);
    }

}
