package com.payments.webhook_service.type;

public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    ACCOUNT_BLOCKED,
    BANK_SERVICE_UNAVAILABLE
}
