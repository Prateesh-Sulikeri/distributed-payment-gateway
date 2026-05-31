package com.payments.settlement_service.service;

import com.payments.settlement_service.domain.Settlement;
import com.payments.settlement_service.domain.SettlementCreationRequest;
import com.payments.settlement_service.domain.SettlementPaymentRecord;
import com.payments.settlement_service.domain.SettlementStatus;
import com.payments.settlement_service.repository.SettlementPaymentRecordRepository;
import com.payments.settlement_service.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementWriterService {

    private final SettlementRepository settlementRepository;
    private final SettlementPaymentRecordRepository settlementPaymentRecordRepository;

    public void createSettlement(
            SettlementCreationRequest request
    ) {
        Settlement settlement =
                Settlement.builder()
                        .merchantId(request.merchantId())
                        .totalAmount(request.totalAmount())
                        .paymentCount(
                                request.paymentCount().intValue()
                        )
                        .periodDate(request.periodDate())
                        .status(SettlementStatus.COMPLETED)
                        .build();

        settlementRepository.save(settlement);

        List<SettlementPaymentRecord> records = settlementPaymentRecordRepository.findByMerchantIdAndSettledFalse(
                request.merchantId()
        );

        records.forEach(record -> {
            record.setSettled(true);
            record.setSettlementId(
                    settlement.getId()
            );
        });

        settlementPaymentRecordRepository.saveAll(records);
    }
}
