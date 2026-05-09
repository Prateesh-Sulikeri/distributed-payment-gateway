package com.payments.payment_service.payment.cache;

import com.payments.payment_service.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCacheService {
    private final RedisTemplate<String, PaymentResponse> redisTemplate;

    private static final String CACHE_KEY = "payment:";
    private static final long CACHE_TTL_HOURS = 24;
    /**
     *
     * Caches with RedisTemplate with key as cacheKey and value as object of PaymentResponse
     *
     * @param idempotencyKey idempotencyKey to be used as key for caching
     * @param response object of PaymentResponse to be cached
     */
    public void cacheByIdempotencyKey(String idempotencyKey, PaymentResponse response) {
        String cacheKey = CACHE_KEY + idempotencyKey;

        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache Payment with idempotencyKey as cache key: {} ", e.getMessage());
        }
    }

    /**
     *
     * Caches with RedisTemplate with key as cacheKey and value as object of PaymentResponse
     *
     * @param paymentId idempotencyKey to be used as key for caching
     * @param response object of PaymentResponse to be cached
     */
    public void cacheById(UUID paymentId, PaymentResponse response) {
        String cacheKey = CACHE_KEY + paymentId;

        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache Payment with paymentId as cacheKey: {} ", e.getMessage());
        }
    }

    public PaymentResponse getByIdempotencyKey(String idempotencyKey) {
        String cacheKey = CACHE_KEY + idempotencyKey;
        try {
            PaymentResponse cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis Unavailable falling through to DB: {}", e.getMessage());
        }
        return null;
    }

    public PaymentResponse getById(UUID id) {
        String cacheKey = CACHE_KEY + id;
        try {
            PaymentResponse cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis Unavailable failing through to DB: {}", e.getMessage());
        }
        return null;
    }

    public void evict(String idempotencyKey, UUID id) {
        try {
            redisTemplate.delete(CACHE_KEY + idempotencyKey);
            redisTemplate.delete(CACHE_KEY + id);
        } catch (Exception e) {
            log.warn("Failed to evict cache: {}", e.getMessage());
        }
    }
}
