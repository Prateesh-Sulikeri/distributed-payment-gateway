package com.payments.webhook_service.event;

import com.payments.webhook_service.type.FailureReason;
import com.payments.webhook_service.type.TransactionStatus;
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

    private String merchantId;
}
