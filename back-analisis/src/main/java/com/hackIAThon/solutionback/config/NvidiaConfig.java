package com.hackIAThon.solutionback.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NvidiaConfig {

    @Value("${nvidia.api-key}")
    private String apiKey;

    @Value("${nvidia.base-url}")
    private String baseUrl;

    @Bean("nvidiaRestClient")
    public RestClient nvidiaRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
