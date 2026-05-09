package com.payments.payment_service.payment.event;

import com.payments.payment_service.common.type.PaymentStatus;
import com.payments.payment_service.common.type.TransactionStatus;
import com.payments.payment_service.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessedConsumer {

    private final PaymentRepository paymentRepository;

    @KafkaListener(
            topics = "payment.processed",
            groupId = "payment-group"
    )
    public void consume(PaymentProcessedEvent event) {
        log.info(
                "Received payment processed event: {}",
                event
        );

        paymentRepository.findById(event.getPaymentId())
                .ifPresent(payment -> {

                    if (event.getTransactionStatus() == TransactionStatus.APPROVED) {

                        payment.setStatus(PaymentStatus.SUCCESS);

                    } else {

                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(
                                event.getFailureReason().name()
                        );
                    }

                    paymentRepository.save(payment);

                    log.info(
                            "Updated payment status for paymentId={} to {}",
                            payment.getId(),
                            payment.getStatus()
                    );
                });
    }
}
