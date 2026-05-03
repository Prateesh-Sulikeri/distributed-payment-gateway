package com.payments.mock_bank_service.service;

import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import org.springframework.stereotype.Service;


public interface BankPaymentService {
    public BankPaymentResponse process(BankPaymentRequest request);
}
