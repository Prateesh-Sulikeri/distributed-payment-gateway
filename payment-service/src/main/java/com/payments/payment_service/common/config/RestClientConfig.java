package com.payments.payment_service.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;


@Configuration
public class RestClientConfig {

    @Value("${mock_bank.url}")
    private String mockBankUrl;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(mockBankUrl)
                .build();
    }
}
