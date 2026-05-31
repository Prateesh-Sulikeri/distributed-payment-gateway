package com.payments.payment_service.payment.event;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {
    private final KafkaTemplate<String, PaymentInitiatedEvent> kafkaTemplate;

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishToDeadLetterFallback")
    public CompletableFuture<SendResult<String, PaymentInitiatedEvent>> publishPaymentInitiated(PaymentInitiatedEvent event ) {

        log.info(
                "Publishing payment initiated event for paymentId={}",
                event.getPaymentId()
        );

        return kafkaTemplate.send(
                "payment.initiated",
                event.getPaymentId().toString(),
                event
        );
    }

    private CompletableFuture<SendResult<String, PaymentInitiatedEvent>> publishToDeadLetterFallback(
            PaymentInitiatedEvent event, Throwable ex) {
        log.error("Failed to publish PaymentInitiatedEvent after retries and circuit breaker for paymentId: {}",
                event.getPaymentId(), ex);
        return CompletableFuture.failedFuture(ex);
    }
}
