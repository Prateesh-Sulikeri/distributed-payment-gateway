package com.payments.payment_service.payment.event.outbox;

import com.payments.payment_service.common.lock.LockService;
import com.payments.payment_service.common.type.EventStatus;
import com.payments.payment_service.common.type.EventType;
import com.payments.payment_service.payment.event.PaymentEventProducer;
import com.payments.payment_service.payment.event.PaymentInitiatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {
    private final OutboxRepository outboxRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;
    private final LockService lockService;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishPendingEvents() {
        String lockName = "outbox-publisher";
        int lockDuration = 30;

        boolean lockAcquired = lockService.acquireLock(lockName, lockDuration);

        if (!lockAcquired) {
            log.debug("Could not acquire lock, another instance is publishing");
            return;
        }

        try {
            log.debug("Checking for pending outbox events...");

            List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(EventStatus.PENDING);
            if (pendingEvents.isEmpty()) {
                log.debug("No pending outbox events");
                return;
            }

            log.info("Found {} pending outbox events", pendingEvents.size());

            for (OutboxEvent outboxEvent : pendingEvents) {
                publishEvent(outboxEvent);
            }
        } finally {
            lockService.releaseLock(lockName);
            log.debug("Lock released: {}", lockName);
        }
    }

    private void publishEvent(OutboxEvent outboxEvent) {
        try {
            if (outboxEvent.getEventType() == EventType.PAYMENT_INITIATED) {
                PaymentInitiatedEvent event = objectMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentInitiatedEvent.class
                );
                paymentEventProducer
                        .publishPaymentInitiated(event)
                        .get();
            }

            outboxEvent.setStatus(EventStatus.SUCCESS);
            outboxRepository.save(outboxEvent);
            log.info("Published outbox event {}", outboxEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to publish outbox event {}", outboxEvent.getEventId(), e);
            outboxEvent.setStatus(EventStatus.PENDING);
            outboxRepository.save(outboxEvent);
        }
    }
}
