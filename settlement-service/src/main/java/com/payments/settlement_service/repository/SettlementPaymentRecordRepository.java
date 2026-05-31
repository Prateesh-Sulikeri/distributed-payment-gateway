package com.payments.settlement_service.repository;

import com.payments.settlement_service.domain.SettlementPaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SettlementPaymentRecordRepository extends JpaRepository<SettlementPaymentRecord, UUID> {
    boolean existsByPaymentId(UUID paymentId);

    @Query(
            """
                SELECT new com.payments.settlement_service.repository.MerchantSettlementAggregate(
                            r.merchantId,
                            SUM(r.amount),
                            COUNT(r)
                        )
                        FROM SettlementPaymentRecord r
                        WHERE r.settled = false
                        GROUP BY r.merchantId
            """
    )
    List<MerchantSettlementAggregate> findUnsettledAggregates();

    List<SettlementPaymentRecord> findByMerchantIdAndSettledFalse(UUID merchantId);
}
