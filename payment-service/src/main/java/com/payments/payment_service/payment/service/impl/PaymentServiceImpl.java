package com.payments.payment_service.payment.service.impl;

import com.payments.payment_service.common.exception.PaymentNotFoundException;
import com.payments.payment_service.common.type.EventStatus;
import com.payments.payment_service.common.type.EventType;
import com.payments.payment_service.payment.cache.PaymentCacheService;
import com.payments.payment_service.payment.dto.PaymentRequest;
import com.payments.payment_service.payment.dto.PaymentResponse;
import com.payments.payment_service.payment.entity.Payment;
import com.payments.payment_service.common.type.PaymentStatus;
import com.payments.payment_service.payment.event.PaymentEventProducer;
import com.payments.payment_service.payment.event.PaymentInitiatedEvent;
import com.payments.payment_service.payment.event.outbox.OutboxEvent;
import com.payments.payment_service.payment.event.outbox.OutboxRepository;
import com.payments.payment_service.payment.repository.PaymentRepository;
import com.payments.payment_service.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.payments.payment_service.payment.mapper.PaymentMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentCacheService paymentCacheService;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    @Transactional
    @Override
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey, UUID merchantId) {

        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("Idempotency-Key is required");

        PaymentResponse cachedPayment = paymentCacheService.getByIdempotencyKey(idempotencyKey);
        if (cachedPayment != null) return cachedPayment;
        // Flow through to DB in case of redis failure
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    PaymentResponse response = paymentMapper.toResponse(existing);
                    paymentCacheService.cacheByIdempotencyKey(idempotencyKey, response);
                    paymentCacheService.cacheById(response.getId(), response);
                    return response;
                })
                .orElseGet(() -> {
                    Payment payment = paymentMapper.toEntity(request, idempotencyKey);
                    payment.setMerchantId(merchantId);
                    try {
                        paymentRepository.saveAndFlush(payment);
                        PaymentInitiatedEvent event =
                                PaymentInitiatedEvent.builder()
                                        .paymentId(payment.getId())
                                        .idempotencyKey(payment.getIdempotencyKey())
                                        .amount(payment.getAmount())
                                        .currency(payment.getCurrency())
                                        .paymentMethod(payment.getPaymentMethod())
                                        .description(payment.getDescription())
                                        .merchantId(merchantId)
                                        .createdAt(payment.getCreatedAt())
                                        .build();

                        OutboxEvent outboxEvent = OutboxEvent.builder()
                                .eventType(EventType.PAYMENT_INITIATED)
                                .payload(objectMapper.writeValueAsString(event))
                                .status(EventStatus.PENDING)
                                .build();
                        outboxRepository.save(outboxEvent);
                        PaymentResponse response = paymentMapper.toResponse(payment);
                        paymentCacheService.cacheByIdempotencyKey(idempotencyKey, response);
                        paymentCacheService.cacheById(payment.getId(), response);
                        return response;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Duplicate insert caught for key: {}, fetching existing", idempotencyKey);
                        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                                .map(paymentMapper::toResponse)
                                .orElseThrow();
                    }
                });
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PaymentResponse> getAllPayments(int page, int size) {
        int cappedSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, cappedSize);

        return paymentRepository.findAll(pageable)
                .map(paymentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentResponse getPaymentById(UUID id) {
        PaymentResponse cachedById = paymentCacheService.getById(id);
        if (cachedById != null) return cachedById;
        PaymentResponse response = paymentMapper.toResponse(paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException("Payment Not Found for id: " + id)));
        paymentCacheService.cacheById(id, response);
        return response;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PaymentResponse> getPaymentByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable)
                .map(paymentMapper::toResponse);
    }
}
