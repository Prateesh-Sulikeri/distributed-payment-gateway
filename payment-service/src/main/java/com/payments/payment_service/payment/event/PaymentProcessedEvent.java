package com.payments.payment_service.payment.event;

import com.payments.payment_service.common.type.FailureReason;
import com.payments.payment_service.common.type.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {
    private UUID paymentId;

    private TransactionStatus transactionStatus;

    private FailureReason failureReason;

    private UUID merchantId;
}