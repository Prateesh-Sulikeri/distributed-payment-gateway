package com.payments.payment_service.payment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {
    private final KafkaTemplate<String, PaymentInitiatedEvent> kafkaTemplate;

    public void publishPaymentInitiated( PaymentInitiatedEvent event ) {
        kafkaTemplate.send(
                "payment.initiated",
                event.getPaymentId().toString(),
                event
        );

        log.info(
                "Publish payment initiated event for PaymentId: {}",
                event.getPaymentId()
        );
    }
}
