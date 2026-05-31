package com.payments.settlement_service.service;

import com.payments.settlement_service.repository.MerchantSettlementAggregate;

import java.util.List;

public interface SettlementAggregationService {
    public List<MerchantSettlementAggregate> getAggregates();
}
