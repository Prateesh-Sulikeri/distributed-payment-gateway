package com.payments.settlement_service.domain;

import com.payments.settlement_service.type.CurrencyCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_payment_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementPaymentRecord {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currency;

    @Column(nullable = false)
    private boolean settled;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "payment_created_at", nullable = false)
    private Instant paymentCreatedAt;
}
