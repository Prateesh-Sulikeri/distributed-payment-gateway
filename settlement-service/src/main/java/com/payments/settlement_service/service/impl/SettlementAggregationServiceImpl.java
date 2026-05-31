package com.payments.settlement_service.service.impl;

import com.payments.settlement_service.repository.MerchantSettlementAggregate;
import com.payments.settlement_service.repository.SettlementPaymentRecordRepository;
import com.payments.settlement_service.service.SettlementAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementAggregationServiceImpl implements SettlementAggregationService {

    private final SettlementPaymentRecordRepository repository;


    @Override
    public List<MerchantSettlementAggregate> getAggregates() {
        return repository.findUnsettledAggregates();
    }
}
