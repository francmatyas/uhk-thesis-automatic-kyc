package com.francmatyas.uhk_thesis_automatic_kyc_api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Podpora pro Instant / LocalDateTime apod.
        mapper.registerModule(new JavaTimeModule());

        // Neselhat na neznámých JSON polích
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Nezapisovat datumy jako timestampy
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Pokud chcete snake_case JSON (volitelné)
        // mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        return mapper;
    }
}
