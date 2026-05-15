package com.payments.mock_bank_service.processor;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.type.FailureReason;
import com.payments.mock_bank_service.type.TransactionStatus;

import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractPaymentProcessor implements PaymentProcessor{

    protected void simulateLatency(int min, int max) {
        int delay = (min >= max) ? min : ThreadLocalRandom.current().nextInt(min, max+1);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    protected FailureReason randomFailureReason(FailureReason... reasons) {
        return reasons[
                    ThreadLocalRandom.current().nextInt(reasons.length)
        ];
    }

    protected boolean isApproved(double successRate) {
        return ThreadLocalRandom.current().nextDouble() < successRate;
    }

    protected BankPaymentResponse approvedResponse(
            BankPaymentRequest request
    ) {

        return BankPaymentResponse.builder()
                .paymentId(request.getPaymentId())
                .transactionStatus(TransactionStatus.APPROVED)
                .reason(null)
                .build();
    }

    protected BankPaymentResponse declinedResponse(
            BankPaymentRequest request,
            FailureReason failureReason
    ) {

        return BankPaymentResponse.builder()
                .paymentId(request.getPaymentId())
                .transactionStatus(TransactionStatus.DECLINED)
                .reason(failureReason)
                .build();
    }
}
