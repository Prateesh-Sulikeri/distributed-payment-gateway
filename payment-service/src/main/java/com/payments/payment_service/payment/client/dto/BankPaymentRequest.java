package com.payments.payment_service.payment.client.dto;

import com.payments.payment_service.payment.entity.type.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload sent to mock bank")
public class BankPaymentRequest {
    @Schema(description = "Payment Unique id")
    @NotNull
    private UUID paymentId;

    @Schema(description = "Amount to be charged", example = "100.00")
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @Schema(description = "Currency Code (ISO-4217", example = "INR")
    @NotNull
    @Size(min = 3, max = 3)
    private  String currency;

    @Schema(description = "Payment Method used", example = "UPI")
    @NotNull
    private PaymentMethod paymentMethod;
}