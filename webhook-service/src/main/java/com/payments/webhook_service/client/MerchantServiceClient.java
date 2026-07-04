package com.payments.webhook_service.client;

import com.payments.webhook_service.client.dto.MerchantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;
import java.util.UUID;

@FeignClient(name = "merchant-service", url = "${merchant-service.url}")
public interface MerchantServiceClient {

    @GetMapping("/merchants/{id}")
    Optional<MerchantResponse> getMerchantById(@PathVariable UUID id);
}
