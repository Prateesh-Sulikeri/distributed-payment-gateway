package com.payments.mock_bank_service.event;


import com.payments.mock_bank_service.type.CurrencyCode;
import com.payments.mock_bank_service.type.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
