package com.payments.merchant_service.controller;

import com.payments.merchant_service.dto.MerchantRequest;
import com.payments.merchant_service.dto.MerchantResponse;
import com.payments.merchant_service.entity.Merchant;
import com.payments.merchant_service.mapper.MerchantMapper;
import com.payments.merchant_service.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;

    @PostMapping("/register")
    public ResponseEntity<MerchantResponse> register(
            @RequestBody MerchantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantService.registerMerchant(request));
    }

    @GetMapping("/validate-key")
    public ResponseEntity<MerchantResponse> validateApiKey(@RequestParam String apiKey) {
        Optional<Merchant> merchant = merchantService.validateApiKey(apiKey);

        return merchant.map(value -> ResponseEntity.ok(merchantMapper.toResponse(value))).orElseGet(() -> ResponseEntity.status(401).build());
    }
}
