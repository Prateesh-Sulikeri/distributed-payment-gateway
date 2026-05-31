package com.payments.settlement_service.domain;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record SettlementCreationRequest(
        UUID merchantId,
        BigDecimal totalAmount,
        Long paymentCount,
        LocalDate periodDate
) {
}
