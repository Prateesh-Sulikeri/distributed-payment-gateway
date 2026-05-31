package com.payments.payment_service.payment.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.payments.payment_service.common.type.CurrencyCode;
import com.payments.payment_service.common.type.PaymentMethod;
import com.payments.payment_service.common.type.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ext.javatime.deser.InstantDeserializer;
import tools.jackson.databind.ext.javatime.ser.InstantSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonPropertyOrder({
        "id",
        "idempotencyKey",
        "amount",
        "currency",
        "paymentMethod",
        "status",
        "failureReason",
        "description",
        "createdAt",
        "updatedAt"
})
public class PaymentResponse {
    @Schema(description = "Unique id")
    private UUID id;

    @Schema(description = "The idempotency key", example = "idm-123")
    private String idempotencyKey;

    @Schema(description = "The Amount involved in transaction", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "INR")
    private CurrencyCode currency;

    @Schema(description = "Current Status of the payment", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "Payment method used to pay", example = "UPI")
    private PaymentMethod paymentMethod;

    @Schema(description = "Optional short description about the payment", example = "Payment for new Shoes")
    private String description;

    @Schema(description = "Optional failure reason", example = "Insufficient Balance")
    private String failureReason;

    @Schema(description = "Unique identification of merchant making the payment request")
    private UUID merchantId;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    @Schema(description = "creation timestamp", example = "")
    private Instant createdAt;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    @Schema(description = "updated timestamp", example = "")
    private Instant updatedAt;
}
