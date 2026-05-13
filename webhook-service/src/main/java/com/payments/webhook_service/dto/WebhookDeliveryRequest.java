package com.payments.webhook_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebhookDeliveryRequest {

    private UUID merchantId;

    private UUID paymentId;

    private String webhookUrl;

    private String payload;
}
