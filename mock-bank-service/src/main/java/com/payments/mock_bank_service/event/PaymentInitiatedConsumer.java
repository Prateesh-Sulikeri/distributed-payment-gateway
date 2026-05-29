package com.payments.mock_bank_service.event;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.service.BankPaymentService;
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
public class PaymentInitiatedConsumer {

    private final BankPaymentService bankPaymentService;
    private final PaymentProcessedProducer paymentProcessedProducer;

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
    @KafkaListener(topics = "payment.initiated", groupId = "mock-bank-group")
    public void consume(PaymentInitiatedEvent event) {
        log.info("Received payment initiated event: {}" , event);

        BankPaymentRequest request =
                BankPaymentRequest.builder()
                        .paymentId(event.getPaymentId())
                        .amount(event.getAmount())
                        .currency(event.getCurrency())
                        .paymentMethod(event.getPaymentMethod())
                        .build();

        BankPaymentResponse response = bankPaymentService.process(request);

        PaymentProcessedEvent processedEvent = PaymentProcessedEvent.builder()
                .paymentId(response.getPaymentId())
                .transactionStatus(response.getTransactionStatus())
                .failureReason(response.getReason())
                .merchantId(event.getMerchantId())
                .build();

        paymentProcessedProducer.publish(processedEvent);
    }
}
