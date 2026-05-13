package com.payments.payment_service.common.lock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "distributed_locks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistributedLock {
    @Id
    private String lockName;

    @Column(nullable = false)
    private String lockedBy;  // Instance identifier

    @CreationTimestamp
    @Column(nullable = false)
    private Instant lockedAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
