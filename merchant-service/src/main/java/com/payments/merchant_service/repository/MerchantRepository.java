package com.payments.merchant_service.repository;

import com.payments.merchant_service.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByApiKeyHash(String hashApiKey);
}
