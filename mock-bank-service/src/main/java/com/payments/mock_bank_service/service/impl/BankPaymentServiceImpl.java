package com.payments.mock_bank_service.service.impl;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.processor.PaymentProcessor;
import com.payments.mock_bank_service.processor.PaymentRouter;
import com.payments.mock_bank_service.type.FailureReason;
import com.payments.mock_bank_service.type.TransactionStatus;
import com.payments.mock_bank_service.service.BankPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankPaymentServiceImpl implements BankPaymentService {

    private final PaymentRouter paymentRouter;

    @Override
    public BankPaymentResponse process(BankPaymentRequest request) {
        log.info(
                "Processing paymentId={} method={}",
                request.getPaymentId(),
                request.getPaymentMethod()
        );

        PaymentProcessor paymentProcessor = paymentRouter.getProcessor(request.getPaymentMethod());

        return paymentProcessor.process(request);
    }
}
