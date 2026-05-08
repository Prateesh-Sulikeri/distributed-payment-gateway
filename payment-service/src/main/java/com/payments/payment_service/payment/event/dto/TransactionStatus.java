package com.payments.payment_service.payment.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Transaction Status from Bank")
public enum TransactionStatus {
    APPROVED,
    DECLINED
}
