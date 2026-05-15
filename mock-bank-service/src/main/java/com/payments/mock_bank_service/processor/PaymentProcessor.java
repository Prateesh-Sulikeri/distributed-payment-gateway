package com.payments.mock_bank_service.processor;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;

public interface PaymentProcessor {

    BankPaymentResponse process(BankPaymentRequest request);
}
