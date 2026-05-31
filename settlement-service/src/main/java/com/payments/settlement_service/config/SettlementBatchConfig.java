package com.payments.settlement_service.config;

import com.payments.settlement_service.domain.SettlementProcessor;
import com.payments.settlement_service.domain.SettlementTasklet;
import com.payments.settlement_service.repository.MerchantSettlementAggregate;
import com.payments.settlement_service.service.SettlementAggregationService;
import com.payments.settlement_service.service.SettlementWriterService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class SettlementBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SettlementAggregationService aggregationService;
    private final SettlementProcessor settlementProcessor;
    private final SettlementWriterService settlementWriterService;

    @Bean
    public ListItemReader<MerchantSettlementAggregate> settlementReader() {
        return new ListItemReader<>(
                aggregationService.getAggregates()
        );
    }

    @Bean
    public Step settlementStep(
            SettlementTasklet settlementTasklet
    ) {
        return new StepBuilder(
                "settlementStep",
                jobRepository
        )
                .tasklet(settlementTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job settlementJob(Step settlementStep) {
        return new JobBuilder(
                "settlementJob",
                jobRepository
        )
                .start(settlementStep)
                .build();
    }

}
