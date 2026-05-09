package com.payments.mock_bank_service.service.impl;

import com.payments.mock_bank_service.config.BankConfig;
import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.type.FailureReason;
import com.payments.mock_bank_service.type.TransactionStatus;
import com.payments.mock_bank_service.service.BankPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class BankPaymentServiceImpl implements BankPaymentService {

    private final BankConfig bankConfig;

    private void simulateLatency() {
        int min = bankConfig.getMinLatencyMs();
        int max = bankConfig.getMaxLatencyMs();
        int delay = (min >= max) ? min : ThreadLocalRandom.current().nextInt(min, max+1);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private FailureReason randomFailureReason() {
        FailureReason[] values = FailureReason.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    @Override
    public BankPaymentResponse process(BankPaymentRequest request) {
        simulateLatency();

        boolean approved = ThreadLocalRandom.current().nextDouble() < bankConfig.getSuccessRate();

        if (approved) {
            return BankPaymentResponse.builder()
                    .paymentId(request.getPaymentId())
                    .transactionStatus(TransactionStatus.APPROVED)
                    .reason(null)
                    .build();
        }

        return BankPaymentResponse.builder()
                .paymentId(request.getPaymentId())
                .transactionStatus(TransactionStatus.DECLINED)
                .reason(randomFailureReason())
                .build();
    }
}
