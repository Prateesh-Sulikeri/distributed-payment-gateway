package com.payments.webhook_service.service;

import com.payments.webhook_service.client.dto.MerchantResponse;
import com.payments.webhook_service.dto.WebhookDeliveryResponse;
import com.payments.webhook_service.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryService {
    public void deliverWebhook(WebhookDelivery delivery);

    public Page<WebhookDeliveryResponse> getAllPendingWebhooks(int page, int size);

    public Page<WebhookDeliveryResponse> getAllDeadWebhooks(int page, int size);

    public void retryWebhook(UUID id);
}
