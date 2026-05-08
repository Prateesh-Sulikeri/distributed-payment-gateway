package com.payments.payment_service.payment.service.impl;

import com.payments.payment_service.common.exception.PaymentNotFoundException;
import com.payments.payment_service.payment.cache.PaymentCacheService;
import com.payments.payment_service.payment.dto.PaymentRequest;
import com.payments.payment_service.payment.dto.PaymentResponse;
import com.payments.payment_service.payment.entity.Payment;
import com.payments.payment_service.payment.entity.type.PaymentStatus;
import com.payments.payment_service.payment.event.PaymentEventProducer;
import com.payments.payment_service.payment.event.PaymentInitiatedEvent;
import com.payments.payment_service.payment.processor.PaymentProcessor;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentProcessor paymentProcessor;
    private final PaymentCacheService paymentCacheService;
    private final PaymentEventProducer paymentEventProducer;

    /**
     * Creates a payment in an idempotent manner.
     *
     * <p>The method first attempts to retrieve an existing payment response
     * from Redis cache using the provided idempotency key. If the cache
     * lookup fails or no cached response exists, the database is checked
     * for an existing payment associated with the same idempotency key.
     *
     * <p>If no existing payment is found:
     * <ul>
     *     <li>A new {@link Payment} entity is created from the request</li>
     *     <li>The payment is persisted with an initial PENDING status</li>
     *     <li>The payment is processed through the {@link PaymentProcessor}</li>
     *     <li>The updated payment state is persisted and cached</li>
     * </ul>
     *
     * <p>To ensure idempotency under concurrent requests, database uniqueness
     * constraints are relied upon. If a duplicate insert occurs, the existing
     * payment is retrieved and returned instead.
     *
     * @param request the payment request containing payment details
     * @param idempotencyKey unique key used to guarantee idempotent payment creation
     * @return the newly created or previously existing payment response
     * @throws IllegalArgumentException if the idempotency key is null or blank
     */
    @Transactional
    @Override
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("Idempotency-Key is required");

        PaymentResponse cachedByIdempotencyKey = paymentCacheService.getByIdempotencyKey(idempotencyKey);
        if (cachedByIdempotencyKey != null) return cachedByIdempotencyKey;

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    PaymentResponse response = paymentMapper.toResponse(existing);
                    paymentCacheService.cacheByIdempotencyKey(idempotencyKey, response);
                    paymentCacheService.cacheById(response.getId(), response);
                    return response;
                })
                .orElseGet(() -> {
                    Payment payment = paymentMapper.toEntity(request, idempotencyKey);

                    try {
                        paymentRepository.saveAndFlush(payment);
//                        paymentProcessor.process(payment);
                        PaymentInitiatedEvent event =
                                PaymentInitiatedEvent.builder()
                                        .paymentId(payment.getId())
                                        .idempotencyKey(payment.getIdempotencyKey())
                                        .amount(payment.getAmount())
                                        .currency(payment.getCurrency())
                                        .paymentMethod(payment.getPaymentMethod())
                                        .description(payment.getDescription())
                                        .createdAt(payment.getCreatedAt())
                                        .build();

                        paymentEventProducer.publishPaymentInitiated(event);
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
