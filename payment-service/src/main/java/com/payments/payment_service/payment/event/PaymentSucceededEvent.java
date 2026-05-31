package com.payments.payment_service.payment.event;

import com.payments.payment_service.common.type.CurrencyCode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentSucceededEvent (
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        CurrencyCode currency,
        Instant createdAt
) {
}
