package com.payments.webhook_service.job;

import com.payments.webhook_service.entity.WebhookDelivery;
import com.payments.webhook_service.repository.WebhookDeliveryRepository;
import com.payments.webhook_service.service.WebhookDeliveryService;
import com.payments.webhook_service.type.WebhookDeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryJob {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDeliveryService webhookDeliveryService;

    @Scheduled(fixedRate = 10000)
    public void retryPendingWebhooks() {
        log.debug("Checking for pending webhooks to retry...");

        List<WebhookDelivery> pendingWebhooks = webhookDeliveryRepository.
                findByStatusInAndNextRetryAtBefore(
                        List.of(
                                WebhookDeliveryStatus.PENDING,
                                WebhookDeliveryStatus.FAILED
                        ),
                        Instant.now()
                );
        log.info("Found {} pending webhooks to retry", pendingWebhooks.size());

        for (WebhookDelivery delivery : pendingWebhooks) {
            webhookDeliveryService.deliverWebhook(delivery);
        }
    }
}
