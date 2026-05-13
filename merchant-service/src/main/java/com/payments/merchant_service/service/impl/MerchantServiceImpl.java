package com.payments.merchant_service.service.impl;

import com.payments.merchant_service.dto.MerchantRequest;
import com.payments.merchant_service.dto.MerchantResponse;
import com.payments.merchant_service.entity.Merchant;
import com.payments.merchant_service.mapper.MerchantMapper;
import com.payments.merchant_service.repository.MerchantRepository;
import com.payments.merchant_service.security.ApiKeyGenerator;
import com.payments.merchant_service.service.MerchantService;
import com.payments.merchant_service.type.MerchantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final MerchantMapper merchantMapper;

    @Transactional
    @Override
    public MerchantResponse registerMerchant(MerchantRequest request) {
        String rawApiKey = apiKeyGenerator.generateApiKey();
        String hashApiKey = apiKeyGenerator.hashApiKey(rawApiKey);

        Merchant merchant = merchantMapper.toEntity(request);
        merchant.setApiKeyHash(hashApiKey);
        merchant.setStatus(MerchantStatus.ACTIVE);

        merchantRepository.save(merchant);

        MerchantResponse response = merchantMapper.toResponse(merchant);
        response.setApiKey(rawApiKey);

        return response;
    }

    @Override
    public Optional<Merchant> validateApiKey(String rawApiKey) {

        String hashApiKey = apiKeyGenerator.hashApiKey(rawApiKey);

        return merchantRepository.findByApiKeyHash(hashApiKey);
    }

    @Override
    public MerchantResponse getMerchantById(UUID merchantId) {
        return merchantMapper.toResponse(merchantRepository.findById(merchantId).orElseThrow(() -> new RuntimeException("Merchant not found")));
    }

    @Override
    public MerchantResponse updateMerchant(UUID merchantId, MerchantRequest request) {

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Merchant Id"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            merchant.setEmail(request.getEmail());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            merchant.setName(request.getName());
        }

        if (request.getWebhookUrl() != null && !request.getWebhookUrl().isBlank()) {
            merchant.setWebhookUrl(request.getWebhookUrl());
        }

        return merchantMapper.toResponse(merchantRepository.save(merchant));
    }

    @Override
    @Transactional
    public MerchantResponse rotateApiKey(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Merchant id"));

        String rawApiKey = apiKeyGenerator.generateApiKey();
        String hashApiKey = apiKeyGenerator.hashApiKey(rawApiKey);
        merchant.setApiKeyHash(hashApiKey);
        merchantRepository.save(merchant);

        MerchantResponse response = merchantMapper.toResponse(merchant);
        response.setApiKey(rawApiKey);

        return response;
    }
}
