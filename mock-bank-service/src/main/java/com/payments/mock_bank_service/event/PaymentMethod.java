package com.payments.mock_bank_service.event;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Different Payment Methods supported")
public enum PaymentMethod {
    UPI,
    CARD,
    NET_BANKING
}
