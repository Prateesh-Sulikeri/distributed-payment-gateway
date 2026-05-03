package com.payments.mock_bank_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Reason for failure (if declined)",
        example = "INSUFFICIENT_FUNDS"
)
public enum FailureReason {
    INSUFFICIENT_FUNDS,
    BANK_TIMEOUT,
    ACCOUNT_BLOCKED
}
