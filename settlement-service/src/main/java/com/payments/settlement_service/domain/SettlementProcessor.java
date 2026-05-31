package com.payments.settlement_service.domain;

import com.payments.settlement_service.repository.MerchantSettlementAggregate;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class SettlementProcessor implements ItemProcessor<MerchantSettlementAggregate, SettlementCreationRequest> {
    @Override
    public @Nullable SettlementCreationRequest process(MerchantSettlementAggregate item) throws Exception {
        return SettlementCreationRequest.builder()
                .merchantId(item.merchantId())
                .totalAmount(item.totalAmount())
                .paymentCount(item.paymentCount())
                .periodDate(LocalDate.now())
                .build();
    }
}
