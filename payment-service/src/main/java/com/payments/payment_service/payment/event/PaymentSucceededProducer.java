package com.payments.payment_service.payment.event;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSucceededProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(
            name = "kafkaProducer",
            fallbackMethod = "publishToDeadLetterFallback"
    )
    public CompletableFuture<SendResult<String, Object>> publishPaymentSucceeded(PaymentSucceededEvent event) {
        log.info(
                "Publishing payment succeeded event for paymentId={}",
                event.paymentId()
        );

        return kafkaTemplate.send(
                "payment.succeeded",
                event.paymentId().toString(),
                event
        );
    }

    private CompletableFuture<SendResult<String, Object>> publishToDeadLetterFallback(
            PaymentSucceededEvent event, Throwable ex
    ) {
        log.error("Failed to publish PaymentSucceededEvent after retries and circuit breaker for paymentId: {}",
                event.paymentId(), ex);

        return CompletableFuture.failedFuture(ex);
    }
}
