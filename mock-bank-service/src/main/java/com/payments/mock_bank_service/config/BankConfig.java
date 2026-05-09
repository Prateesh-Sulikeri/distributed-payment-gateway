package com.payments.mock_bank_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bank")
@Getter
@Setter
public class BankConfig {
    private double successRate;
    private int minLatencyMs;
    private int maxLatencyMs;
}
