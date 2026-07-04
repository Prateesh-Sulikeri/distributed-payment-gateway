package com.payments.mock_bank_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bank")
@Getter
@Setter
public class BankConfig {

    @NestedConfigurationProperty
    private final Processor card = new Processor();

    @NestedConfigurationProperty
    private final Processor upi = new Processor();

    @NestedConfigurationProperty
    private final Processor netBanking = new Processor();

    @Getter
    @Setter
    public static class Processor {
        private double successRate;
        private int minLatencyMs;
        private int maxLatencyMs;
    }
}
