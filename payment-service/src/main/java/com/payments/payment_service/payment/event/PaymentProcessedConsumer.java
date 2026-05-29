package com.payments.payment_service.payment.event;

import com.payments.payment_service.common.type.FailureReason;
import com.payments.payment_service.common.type.PaymentStatus;
import com.payments.payment_service.common.type.TransactionStatus;
import com.payments.payment_service.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessedConsumer {

    private final PaymentRepository paymentRepository;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(
                    delay = 2000,
                    multiplier = 2.0
            ),
            dltTopicSuffix = ".DLT",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoStartDltHandler = "false"
    )
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

                    // Idempotency check
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        log.warn(
                                "Duplicate payment processed event ignored for paymentId={} currentStatus={}",
                                payment.getId(),
                                payment.getStatus()
                        );
                        return;
                    }

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
