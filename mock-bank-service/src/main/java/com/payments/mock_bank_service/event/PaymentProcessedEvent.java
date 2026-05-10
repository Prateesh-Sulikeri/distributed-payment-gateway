package com.payments.mock_bank_service.event;

import com.payments.mock_bank_service.type.FailureReason;
import com.payments.mock_bank_service.type.TransactionStatus;
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
