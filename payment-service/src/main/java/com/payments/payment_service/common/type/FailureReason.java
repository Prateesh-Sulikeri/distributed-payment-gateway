package com.payments.payment_service.common.type;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Failure reason from Bank")
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    ACCOUNT_BLOCKED,
    CARD_EXPIRED,
    CVV_MISMATCH,
    UPI_ID_NOT_FOUND,
    DAILY_LIMIT_EXCEEDED,
    SESSION_EXPIRED
}
