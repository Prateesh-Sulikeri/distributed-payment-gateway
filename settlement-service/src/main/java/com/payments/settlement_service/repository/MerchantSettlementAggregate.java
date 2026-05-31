package com.payments.settlement_service.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record MerchantSettlementAggregate(
        UUID merchantId,
        BigDecimal totalAmount,
        Long paymentCount
) {
}

