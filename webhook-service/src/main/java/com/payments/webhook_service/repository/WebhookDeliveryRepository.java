package com.payments.webhook_service.repository;

import com.payments.webhook_service.entity.WebhookDelivery;
import com.payments.webhook_service.type.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByStatusInAndNextRetryAtBefore(List<WebhookDeliveryStatus> statuses, Instant now);

    Page<WebhookDelivery> findByStatus(WebhookDeliveryStatus status, Pageable pageable);

    List<WebhookDelivery> findByMerchantIdAndStatus(UUID merchantId, WebhookDeliveryStatus status);
}
