package com.payments.webhook_service.mapper;

import com.payments.webhook_service.dto.WebhookDeliveryResponse;
import com.payments.webhook_service.entity.WebhookDelivery;
import org.springframework.stereotype.Component;

@Component
public class WebhookMapper {

    public  WebhookDeliveryResponse toResponse(WebhookDelivery delivery) {
        return WebhookDeliveryResponse.builder()
                .id(delivery.getId())
                .merchantId(delivery.getMerchantId())
                .paymentId(delivery.getPaymentId())
                .webhookUrl(delivery.getWebhookUrl())
                .status(delivery.getStatus())
                .attemptCount(delivery.getAttemptCount())
                .nextRetryAt(delivery.getNextRetryAt())
                .lastErrorMessage(delivery.getLastErrorMessage())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }
}
