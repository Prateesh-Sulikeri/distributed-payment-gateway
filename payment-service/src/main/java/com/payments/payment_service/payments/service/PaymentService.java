package com.payments.payment_service.payments.service;

import com.payments.payment_service.payments.dto.PaymentRequest;
import com.payments.payment_service.payments.dto.PaymentResponse;
import com.payments.payment_service.payments.entity.type.PaymentStatus;
import jdk.jshell.Snippet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey);
    public Page<PaymentResponse> getAllPayments(int page, int size);
    public PaymentResponse getPaymentById(UUID id);
    public Page<PaymentResponse> getPaymentByStatus(PaymentStatus status, Pageable pageable);
}
