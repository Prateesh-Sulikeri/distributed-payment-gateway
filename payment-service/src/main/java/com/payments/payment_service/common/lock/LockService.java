package com.payments.payment_service.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockService {

    private final LockRepository lockRepository;

    @Transactional
    public boolean acquireLock(String lockName, int durationSeconds) {
        Instant now = Instant.now();

        DistributedLock newLock = new DistributedLock(
                lockName,
                "payment-service-instance",
                now,
                now.plusSeconds(durationSeconds)
        );

        try {
            lockRepository.save(newLock);
            log.info("Lock Acquired: {}", lockName);
            return true;
        } catch (DataIntegrityViolationException ex) {
            DistributedLock existingLock = lockRepository.findById(lockName).orElse(null);

            if (existingLock == null) return false;

            if (existingLock.getExpiresAt().isBefore(now)) {
                log.info("Expired lock found, deleting: {}", lockName);
                lockRepository.delete(existingLock);
                lockRepository.flush();

                try {
                    lockRepository.save(newLock);
                    log.info("Lock re-acquired after expiry: {}", lockName);
                    return true;
                } catch (Exception retryEx) {
                    return false;
                }
            }
            return false;
        }
    }

    @Transactional
    public void releaseLock(String lockName) {

        lockRepository.deleteById(lockName);

        log.info("Lock released: {}", lockName);
    }
}
