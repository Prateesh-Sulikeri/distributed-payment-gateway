package com.payments.payment_service.common.lock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface LockRepository extends JpaRepository<DistributedLock, String> {
    Optional<DistributedLock> findByLockName(String lockName);
    void deleteByLockNameAndExpiresAtBefore(String lockName, Instant now);
}
