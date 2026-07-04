package com.payments.mock_bank_service.processor;

import com.payments.mock_bank_service.config.BankConfig;
import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.type.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpiProcessor extends AbstractPaymentProcessor{

    private final BankConfig bankConfig;

    @Override
    public BankPaymentResponse process(BankPaymentRequest request) {
        BankConfig.Processor config = bankConfig.getUpi();

        simulateLatency(config.getMinLatencyMs(), config.getMaxLatencyMs());

        if(isApproved(config.getSuccessRate())) {
            return approvedResponse(request);
        }
        return declinedResponse(
                request,
                randomFailureReason(
                        FailureReason.UPI_ID_NOT_FOUND,
                        FailureReason.DAILY_LIMIT_EXCEEDED
                )
        );
    }
}
