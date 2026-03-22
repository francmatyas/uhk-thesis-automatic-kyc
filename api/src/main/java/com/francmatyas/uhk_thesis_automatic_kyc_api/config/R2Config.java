package com.francmatyas.uhk_thesis_automatic_kyc_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class R2Config {

    @Value("${storage.s3.access-key-id}")
    private String accessKeyId;

    @Value("${storage.s3.secret-access-key}")
    private String secretAccessKey;

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    @Value("${storage.s3.region:us-east-1}")
    private String region;

    @Bean
    public AwsBasicCredentials r2Credentials() {
        return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    }

    @Bean
    public S3Client r2S3Client(AwsBasicCredentials creds) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public S3Presigner r2S3Presigner(AwsBasicCredentials creds) {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }
}

