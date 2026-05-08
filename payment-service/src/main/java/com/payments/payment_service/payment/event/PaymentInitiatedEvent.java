package com.payments.payment_service.payment.event;

import com.payments.payment_service.payment.entity.type.CurrencyCode;
import com.payments.payment_service.payment.entity.type.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiatedEvent {

    private UUID paymentId;

    private String idempotencyKey;

    private BigDecimal amount;

    private CurrencyCode currency;

    private PaymentMethod paymentMethod;

    private String description;

    private Instant createdAt;
}
