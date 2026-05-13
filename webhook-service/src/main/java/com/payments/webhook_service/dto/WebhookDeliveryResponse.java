package com.payments.webhook_service.dto;

import com.payments.webhook_service.type.WebhookDeliveryStatus;
import lombok.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebhookDeliveryResponse {

    private UUID id;

    private UUID merchantId;

    private UUID paymentId;

    private String webhookUrl;

    private WebhookDeliveryStatus status;

    private Integer attemptCount;

    private Instant nextRetryAt;

    private String lastErrorMessage;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant deliveredAt;
}
