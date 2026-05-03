package com.payments.payment_service.payments.service.impl;

import com.payments.payment_service.payments.client.BankClient;
import com.payments.payment_service.payments.client.dto.BankPaymentRequest;
import com.payments.payment_service.payments.client.dto.BankPaymentResponse;
import com.payments.payment_service.payments.client.dto.TransactionStatus;
import com.payments.payment_service.payments.dto.PaymentRequest;
import com.payments.payment_service.payments.dto.PaymentResponse;
import com.payments.payment_service.payments.entity.Payment;
import com.payments.payment_service.payments.entity.type.PaymentStatus;
import com.payments.payment_service.payments.repository.PaymentRepository;
import com.payments.payment_service.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final RedisTemplate<String, PaymentResponse> redisTemplate;
    private static final String CACHE_KEY = "payment:";
    private static final long CACHE_TTL_HOURS = 24;
    private final BankClient bankClient;

    private PaymentResponse mapToResponse(Payment payment) {
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

    private void cacheQuietly(String cacheKey, PaymentResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache Payment: {} ", e.getMessage());
        }
    }

    private BankPaymentResponse getBankProcessedResponse(Payment payment) {
        BankPaymentRequest request = BankPaymentRequest.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .build();

        return bankClient.process(request);
    }

    private void mapBankResponseToPayment(BankPaymentResponse bankPaymentResponse, Payment payment) {
        if (bankPaymentResponse.getTransactionStatus() == TransactionStatus.APPROVED) {
            payment.setStatus(PaymentStatus.SUCCESS);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(bankPaymentResponse.getReason().name());
        }
    }

    private Payment processBankPayment(Payment payment) {
        BankPaymentResponse bankPaymentResponse;

        try {
            bankPaymentResponse = getBankProcessedResponse(payment);
            mapBankResponseToPayment(bankPaymentResponse, payment);
        } catch (Exception e) {
            log.warn("Bank processing Failed! Failing payment: {}", e.getMessage() );
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("BANK_SERVICE_UNAVAILABLE");
        }

        return payment;
    }

    /**
     * Creates a new payment in an idempotent manner.
     *
     * <p>This method ensures that repeated requests with the same idempotency key
     * do not create duplicate payment records. The flow is as follows:
     *
     * <ul>
     *     <li>Checks Redis cache for an existing response</li>
     *     <li>Falls back to database lookup using idempotency key</li>
     *     <li>If no existing payment is found, creates a new payment with PENDING status</li>
     *     <li>Handles race conditions using database uniqueness constraints</li>
     *     <li>Caches the response for faster subsequent access</li>
     * </ul>
     *
     * <p><b>Note:</b>the payment is created with a PENDING status.
     * The final status (SUCCESS/FAILED) will be determined by the response from the Mock Bank service.
     *
     * @param request          the payment request containing amount, currency, and payment method
     * @param idempotencyKey   unique key to ensure idempotent payment creation
     * @return                 the created or existing payment response
     * @throws IllegalArgumentException if the request is invalid
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

        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    PaymentResponse response = mapToResponse(existing);
                    cacheQuietly(cacheKey, response);
                    return response;
                })
                .orElseGet(() -> {
                    Payment payment = new Payment();
                    payment.setIdempotencyKey(idempotencyKey);
                    payment.setAmount(request.getAmount());
                    payment.setCurrency(request.getCurrency());
                    payment.setStatus(PaymentStatus.PENDING);
                    payment.setPaymentMethod(request.getPaymentMethod());
                    payment.setDescription(request.getDescription());
                    payment.setFailureReason(null);

                    try {
                        Payment saved = repository.save(payment);
                        repository.flush();
                        Payment updatedPayment = processBankPayment(saved);
                        Payment saveUpdatedPayment = repository.save(updatedPayment);
                        PaymentResponse response = mapToResponse(saveUpdatedPayment);
                        cacheQuietly(cacheKey, response);
                        return response;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Duplicate insert caught for key: {}, fetching existing", idempotencyKey);
                        return repository.findByIdempotencyKey(idempotencyKey)
                                .map(this::mapToResponse)
                                .orElseThrow();
                    }
                });
    }
}
