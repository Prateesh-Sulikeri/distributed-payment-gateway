package com.payments.payment_service.payments.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Failure reason from Bank")
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    ACCOUNT_BLOCKED
}
