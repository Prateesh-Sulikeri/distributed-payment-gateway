package com.payments.mock_bank_service.type;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Different Currencies supported by the Payment Gateway")
public enum CurrencyCode {
    INR,
    USD,
    JPY,
    AUD
}