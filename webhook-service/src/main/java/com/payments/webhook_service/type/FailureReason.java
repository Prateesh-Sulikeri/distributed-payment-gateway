package com.payments.webhook_service.type;

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