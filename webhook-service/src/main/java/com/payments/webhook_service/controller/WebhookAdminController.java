package com.payments.webhook_service.controller;

import com.payments.webhook_service.dto.WebhookDeliveryResponse;
import com.payments.webhook_service.service.WebhookDeliveryService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/webhooks")
public class WebhookAdminController {

    private final WebhookDeliveryService webhookDeliveryService;

    @GetMapping("/status-pending")
    public ResponseEntity<Page<WebhookDeliveryResponse>> getAllPendingWebhooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(webhookDeliveryService.getAllPendingWebhooks(page, size));
    }

    @GetMapping("/status-dead")
    public ResponseEntity<Page<WebhookDeliveryResponse>> getAllDeadWebhooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(webhookDeliveryService.getAllDeadWebhooks(page, size));
    }

    @PostMapping("/retry/{id}")
    public ResponseEntity<Void> retryWebhook(
            @PathVariable UUID id
    ) {

        webhookDeliveryService.retryWebhook(id);

        return ResponseEntity.noContent().build();
    }
}
