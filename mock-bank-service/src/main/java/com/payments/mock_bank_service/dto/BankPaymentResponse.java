package com.payments.mock_bank_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BankPaymentResponse {
    @Schema(description = "Payment ID received from Payment Service")
    private UUID paymentId;

    @Schema(description = "Status of the payment", example = "APPROVED")
    private TransactionStatus transactionStatus;

    @Schema(description = "reason for failure", example = "Insufficient Balance")
    private FailureReason reason;
}
