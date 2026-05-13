package com.payments.merchant_service.mapper;


import com.payments.merchant_service.dto.MerchantRequest;
import com.payments.merchant_service.dto.MerchantResponse;
import com.payments.merchant_service.entity.Merchant;
import org.springframework.stereotype.Component;

@Component
public class MerchantMapper {
    public MerchantResponse toResponse(Merchant merchant) {
        return MerchantResponse.builder()
                .merchantId(merchant.getId())
                .name(merchant.getName())
                .email(merchant.getEmail())
                .apiKey(merchant.getApiKey())
                .webhookUrl(merchant.getWebhookUrl())
                .createdAt(merchant.getCreatedAt())
                .status(merchant.getStatus())
                .build();
    }

    public Merchant toEntity(MerchantRequest request) {
        return Merchant.builder()
                .name(request.getName())
                .email(request.getEmail())
                .webhookUrl(request.getWebhookUrl())
                .build();
    }
}
