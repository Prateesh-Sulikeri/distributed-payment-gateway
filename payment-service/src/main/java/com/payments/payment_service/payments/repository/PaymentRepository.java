package com.payments.payment_service.payments.repository;

import com.payments.payment_service.payments.entity.Payment;
import com.payments.payment_service.payments.entity.type.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
