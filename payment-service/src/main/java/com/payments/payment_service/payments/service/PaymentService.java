package com.payments.payment_service.payments.service;

import com.payments.payment_service.payments.dto.PaymentRequest;
import com.payments.payment_service.payments.dto.PaymentResponse;

public interface PaymentService {
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey);
}
