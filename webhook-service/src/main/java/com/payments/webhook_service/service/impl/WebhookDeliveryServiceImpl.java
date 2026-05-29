package com.payments.webhook_service.service.impl;

import com.payments.webhook_service.client.dto.MerchantResponse;
import com.payments.webhook_service.dto.WebhookDeliveryResponse;
import com.payments.webhook_service.entity.WebhookDelivery;
import com.payments.webhook_service.mapper.WebhookMapper;
import com.payments.webhook_service.repository.WebhookDeliveryRepository;
import com.payments.webhook_service.service.WebhookDeliveryService;
import com.payments.webhook_service.type.WebhookDeliveryStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeliveryServiceImpl implements WebhookDeliveryService {

    private final WebhookDeliveryRepository repository;
    private final RestTemplate restTemplate;
    private final WebhookMapper webhookMapper;

    @CircuitBreaker(name = "webhookDelivery", fallbackMethod = "webhookDeliveryFallback")
    @Override
    public void deliverWebhook(WebhookDelivery delivery) {
        try {
            log.info("Attempting to deliver webhook {} to {}", delivery.getId(), delivery.getWebhookUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Webhook-ID", delivery.getId().toString());
            headers.add("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis()));

            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    delivery.getWebhookUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook {} delivered successfully", delivery.getId());
                delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(Instant.now());
                repository.save(delivery);
            } else {
                handleDeliveryFailure(delivery, "HTTP " + response.getStatusCode());
            }
        }
        catch (Exception e) {
            // Network error, timeout, etc.
            log.warn("Webhook {} delivery failed: {}", delivery.getId(), e.getMessage());
            handleDeliveryFailure(delivery, e.getMessage());
            throw e;
        }
    }

    public  void webhookDeliveryFallback(WebhookDelivery delivery, Exception ex) {
        log.error("Webhook {} delivery failed - circuit open or timeout. Error: {}",
                delivery.getId(), ex.getMessage());
    }

    private void handleDeliveryFailure(WebhookDelivery delivery, String errorMessage) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastErrorMessage(errorMessage);

        if (delivery.getAttemptCount() > 5) {
            log.error("Webhook {} failed after 5 attempts. Marking as DEAD", delivery.getId());
            delivery.setStatus(WebhookDeliveryStatus.DEAD);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            int delayedSecond = 30 * (int) Math.pow(2, delivery.getAttemptCount() - 1);
            delivery.setNextRetryAt(Instant.now().plusSeconds(delayedSecond));
            log.info("Webhook {} scheduled for retry in {}s ", delivery.getId(), delayedSecond);
        }

        repository.save(delivery);
    }

    @Override
    public Page<WebhookDeliveryResponse> getAllPendingWebhooks(int page, int size) {
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);

        return repository.findByStatus(WebhookDeliveryStatus.PENDING, pageable)
                .map(webhookMapper::toResponse);
    }

    @Override
    public Page<WebhookDeliveryResponse> getAllDeadWebhooks(int page, int size) {
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);

        return repository.findByStatus(WebhookDeliveryStatus.DEAD, pageable)
                .map(webhookMapper::toResponse);
    }

    @Override
    public void retryWebhook(UUID id) {
        WebhookDelivery delivery = repository.findById(id).orElseThrow(() -> new RuntimeException("Webhook  not found"));

        if (
            delivery.getStatus() != WebhookDeliveryStatus.FAILED &&
            delivery.getStatus() != WebhookDeliveryStatus.DEAD
        ) {
            throw new IllegalStateException(
                    "Only FAILED or DEAD webhooks can be retried"
            );
        }
        log.info("Manually retrying webhook {}", id);

        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextRetryAt(Instant.now());

        repository.save(delivery);
        deliverWebhook(delivery);
    }
}
