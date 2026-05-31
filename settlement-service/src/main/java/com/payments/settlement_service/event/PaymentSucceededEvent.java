package com.payments.settlement_service.event;

import com.payments.settlement_service.type.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent (
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        CurrencyCode currency,
        Instant createdAt
) {
}
