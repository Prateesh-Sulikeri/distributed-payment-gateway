package com.payments.payment_service.payment.client;

import com.payments.payment_service.payment.client.dto.MerchantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@FeignClient(name = "merchant-service", url = "http://localhost:8082")
public interface MerchantServiceClient {

    @GetMapping("/merchants/validate-key")
    Optional<MerchantResponse> validateApiKey(@RequestParam String apiKey);
}
