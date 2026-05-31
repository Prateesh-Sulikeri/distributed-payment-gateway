package com.payments.settlement_service.event;

import com.payments.settlement_service.domain.SettlementPaymentRecord;
import com.payments.settlement_service.repository.SettlementPaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSucceededConsumer {

    private final SettlementPaymentRecordRepository repository;

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
            topics = "payment.succeeded",
            groupId = "settlement-group"
    )
    public void consume(PaymentSucceededEvent event) {
        log.info(
                "Received Payment succeeded event for payment_id {} " ,
                event.paymentId()
        );

        if (repository.existsByPaymentId(event.paymentId())) {
            log.warn(
                    "Duplicate payment event ignored for payment_id={}",
                    event.paymentId()
            );

            return;
        }

        SettlementPaymentRecord record =
                SettlementPaymentRecord.builder()
                        .paymentId(event.paymentId())
                        .merchantId(event.merchantId())
                        .amount(event.amount())
                        .currency(event.currency())
                        .paymentCreatedAt(event.createdAt())
                        .settled(false)
                        .build();

        repository.save(record);

        log.info(
                "Stored payment {} for future settlement",
                event.paymentId()
        );
    }
}
