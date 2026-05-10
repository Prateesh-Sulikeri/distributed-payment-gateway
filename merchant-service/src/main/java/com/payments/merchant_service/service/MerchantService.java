package com.payments.merchant_service.service;

import com.payments.merchant_service.dto.MerchantRequest;
import com.payments.merchant_service.dto.MerchantResponse;
import com.payments.merchant_service.entity.Merchant;

import java.util.Optional;


public interface MerchantService {
    public MerchantResponse registerMerchant(MerchantRequest request);
    public Optional<Merchant> validateApiKey(String rawApiKey);
}
