package com.payments.mock_bank_service.type;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Reason for failure (if declined)",
        example = "INSUFFICIENT_FUNDS"
)
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    CARD_EXPIRED,
    CVV_MISMATCH,
    UPI_ID_NOT_FOUND,
    DAILY_LIMIT_EXCEEDED,
    SESSION_EXPIRED
}
