package com.payments.payment_service.payment.mapper;

import com.payments.payment_service.payment.dto.PaymentRequest;
import com.payments.payment_service.payment.dto.PaymentResponse;
import com.payments.payment_service.payment.entity.Payment;
import com.payments.payment_service.common.type.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    /**
     *
     * maps a Payment object into PaymentResponse object
     *
     * @param payment object
     * @return PaymentResponse object
     */
    public PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    public Payment toEntity(
            PaymentRequest request,
            String idempotencyKey
    ) {
        return Payment.builder()
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .description(request.getDescription())
                .failureReason(null)
                .build();
    }
}
