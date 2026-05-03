package com.payments.payment_service.payments.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankPaymentResponse {
    @Schema(description = "Payment ID received from Payment Service")
    private UUID paymentId;

    @Schema(description = "Status of the payment", example = "APPROVED")
    private TransactionStatus transactionStatus;

    @Schema(description = "reason for failure", example = "Insufficient Balance")
    private FailureReason reason;
}
