package com.payments.mock_bank_service;

import com.payments.mock_bank_service.config.BankConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BankConfig.class)
public class MockBankServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MockBankServiceApplication.class, args);
	}

}
