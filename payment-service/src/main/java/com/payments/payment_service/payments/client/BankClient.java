package com.payments.payment_service.payments.client;

import com.payments.payment_service.payments.client.dto.BankPaymentRequest;
import com.payments.payment_service.payments.client.dto.BankPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client responsible for communicating with the mock bank service.
 */
@Component
@RequiredArgsConstructor
public class BankClient {
    private final RestClient restClient;
    /**
     * Sends payment request to bank for processing.
     *
     * @param request bank payment request payload
     * @return bank payment response
     */
    public BankPaymentResponse process(BankPaymentRequest request) {
        return restClient
                .post()
                .uri("/bank/process")
                .body(request)
                .retrieve()
                .body(BankPaymentResponse.class);
    }
}
