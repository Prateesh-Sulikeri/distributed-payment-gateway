package com.payments.webhook_service.event;

import com.payments.webhook_service.client.MerchantServiceClient;
import com.payments.webhook_service.client.dto.MerchantResponse;
import com.payments.webhook_service.entity.WebhookDelivery;
import com.payments.webhook_service.repository.WebhookDeliveryRepository;
import com.payments.webhook_service.type.WebhookDeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessedConsumer {
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final MerchantServiceClient merchantServiceClient;
    private final ObjectMapper objectMapper;

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
            groupId = "webhook-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentProcessed(PaymentProcessedEvent event) {
        try {
            log.info("Received PaymentProcessed event: {}", event);

            Optional<MerchantResponse> merchant =  merchantServiceClient.getMerchantById(UUID.fromString(event.getMerchantId()));

            if (merchant.isEmpty() || merchant.get().getWebhookUrl() == null) {
                log.warn("Merchant {} has no webhook URL", event.getMerchantId());
                return;
            }

            WebhookDelivery webhookDelivery = WebhookDelivery.builder()
                    .merchantId(UUID.fromString(event.getMerchantId()))
                    .paymentId(event.getPaymentId())
                    .webhookUrl(merchant.get().getWebhookUrl())
                    .payload(objectMapper.writeValueAsString(event))
                    .status(WebhookDeliveryStatus.PENDING)
                    .attemptCount(0)
                    .nextRetryAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            webhookDeliveryRepository.save(webhookDelivery);
            log.info("Created Webhook delivery {} for merchant {}", webhookDelivery.getId(), event.getMerchantId());
        }
        catch (Exception e) {
            log.error("Error processing PaymentProcessed event", e);
        }
    }
}
