package com.payments.mock_bank_service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessedProducer {

    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;

    public void publish(PaymentProcessedEvent event) {
        kafkaTemplate.send(
                "payment.processed",
                event.getPaymentId().toString(),
                event
        );

        log.info(
                "Published payment processed event for paymentId={}",
                event.getPaymentId()
        );
    }
}
