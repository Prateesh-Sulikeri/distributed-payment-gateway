package com.payments.merchant_service.service;

import com.payments.merchant_service.dto.MerchantRequest;
import com.payments.merchant_service.dto.MerchantResponse;
import com.payments.merchant_service.entity.Merchant;

import java.util.Optional;
import java.util.UUID;


public interface MerchantService {
    public MerchantResponse registerMerchant(MerchantRequest request);
    public Optional<Merchant> validateApiKey(String rawApiKey);
    public MerchantResponse getMerchantById(UUID merchantId);
    public MerchantResponse updateMerchant(UUID merchantId, MerchantRequest request);
    public MerchantResponse rotateApiKey(UUID id);
}
