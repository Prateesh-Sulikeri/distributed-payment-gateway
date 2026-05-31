package com.payments.settlement_service.domain;

import com.payments.settlement_service.config.SettlementBatchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final JobOperator jobOperator;
    private final Job settlementJob;

    @Scheduled(cron = "0 0 0 * * *")
    public void runSettlementJob() {

        try {

            JobParameters params = new JobParametersBuilder()
                    .addLong(
                            "timestamp",
                            System.currentTimeMillis()
                    )
                    .toJobParameters();

            JobExecution execution =
                    jobOperator.start(settlementJob, params);

            log.info(
                    "Settlement job started with executionId={}",
                    execution.getId()
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to start settlement job",
                    ex
            );
        }
    }
}
