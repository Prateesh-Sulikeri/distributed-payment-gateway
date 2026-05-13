package com.payments.payment_service.payment.event.outbox;

import com.payments.payment_service.common.type.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(EventStatus status);
}
