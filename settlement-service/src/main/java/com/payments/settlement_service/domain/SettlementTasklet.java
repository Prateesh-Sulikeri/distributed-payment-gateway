package com.payments.settlement_service.domain;

import com.payments.settlement_service.repository.MerchantSettlementAggregate;
import com.payments.settlement_service.service.SettlementAggregationService;
import com.payments.settlement_service.service.SettlementWriterService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SettlementTasklet implements Tasklet {

    private final SettlementAggregationService settlementAggregationService;
    private final SettlementProcessor settlementProcessor;
    private final SettlementWriterService settlementWriterService;

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<MerchantSettlementAggregate> aggregates = settlementAggregationService.getAggregates();

        for (MerchantSettlementAggregate aggregate: aggregates) {
            SettlementCreationRequest request = settlementProcessor.process(aggregate);
            settlementWriterService.createSettlement(request);
        }
        return RepeatStatus.FINISHED;
    }
}
