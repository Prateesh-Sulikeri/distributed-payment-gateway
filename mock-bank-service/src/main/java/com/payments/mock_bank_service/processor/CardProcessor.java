package com.payments.mock_bank_service.processor;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.type.FailureReason;
import org.springframework.stereotype.Component;

@Component
public class CardProcessor extends AbstractPaymentProcessor {

    private static final double SUCCESS_RATE = 0.85;

    private static final int MIN_LATENCY_MS = 200;
    private static final int MAX_LATENCY_MS = 500;

    @Override
    public BankPaymentResponse process(BankPaymentRequest request) {
        simulateLatency(MIN_LATENCY_MS, MAX_LATENCY_MS);

        if (isApproved(SUCCESS_RATE)) {
            return approvedResponse(request);
        }

        return declinedResponse(
                request,
                randomFailureReason(
                        FailureReason.INSUFFICIENT_FUNDS,
                        FailureReason.CARD_EXPIRED,
                        FailureReason.CVV_MISMATCH
                )
        );
    }
}
