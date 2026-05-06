package com.payments.payment_service.payment.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Failure reason from Bank")
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    ACCOUNT_BLOCKED,
    BANK_SERVICE_UNAVAILABLE
}
