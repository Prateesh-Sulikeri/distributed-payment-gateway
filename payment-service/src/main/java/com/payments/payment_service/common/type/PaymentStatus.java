package com.payments.payment_service.common.type;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment Status set by Payment Gateway")
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
