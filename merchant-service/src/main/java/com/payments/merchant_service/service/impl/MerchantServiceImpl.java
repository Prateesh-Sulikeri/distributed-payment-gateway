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
}
