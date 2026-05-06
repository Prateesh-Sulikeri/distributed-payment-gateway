package com.payments.payment_service.payments.service.impl;

import com.payments.payment_service.payments.dto.PaymentRequest;
import com.payments.payment_service.payments.dto.PaymentResponse;
import com.payments.payment_service.payments.entity.Payment;
import com.payments.payment_service.payments.entity.type.PaymentStatus;
import com.payments.payment_service.payments.processor.PaymentProcessor;
import com.payments.payment_service.payments.repository.PaymentRepository;
import com.payments.payment_service.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.payments.payment_service.payments.mapper.PaymentMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, PaymentResponse> redisTemplate;
    private final PaymentMapper paymentMapper;
    private final PaymentProcessor paymentProcessor;

    private static final String CACHE_KEY = "payment:";
    private static final long CACHE_TTL_HOURS = 24;


    /**
     *
     * Caches with RedisTemplate with key as cacheKey and value as object of PaymentResponse
     *
     * @param cacheKey string of cache key which is a concatenation of payment:idempotency_key
     * @param response object of PaymentResponse to be cached
     */
    private void cacheQuietly(String cacheKey, PaymentResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache Payment: {} ", e.getMessage());
        }
    }

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

        String cacheKey = CACHE_KEY + idempotencyKey;

        try {
            PaymentResponse cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis Unavailable falling through to DB: {}", e.getMessage());
        }

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    PaymentResponse response = paymentMapper.toResponse(existing);
                    cacheQuietly(cacheKey, response);
                    return response;
                })
                .orElseGet(() -> {
                    Payment payment = paymentMapper.toEntity(request, idempotencyKey);

                    try {
                        paymentRepository.saveAndFlush(payment);
                        paymentProcessor.process(payment);
                        PaymentResponse response = paymentMapper.toResponse(payment);
                        cacheQuietly(cacheKey, response);
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
        return paymentMapper.toResponse(paymentRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<PaymentResponse> getPaymentByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable)
                .map(paymentMapper::toResponse);
    }
}
