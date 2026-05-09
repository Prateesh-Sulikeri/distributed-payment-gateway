package com.payments.payment_service.payment.dto;

import com.payments.payment_service.common.type.CurrencyCode;
import com.payments.payment_service.common.type.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @Schema(description = "Amount to be charged", example = "100.00")
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @Schema(description = "Currency Code (ISO-4217", example = "INR")
    @NotNull
    private CurrencyCode currency;

    @Schema(description = "Payment Method used", example = "UPI")
    @NotNull
    private PaymentMethod paymentMethod;

    @Schema(description = "Optional description of the payment", example = "Payment for new Shoes")
    private String description;
}
