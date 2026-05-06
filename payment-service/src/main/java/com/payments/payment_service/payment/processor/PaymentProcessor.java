package com.payments.payment_service.payment.processor;

import com.payments.payment_service.payment.client.BankClient;
import com.payments.payment_service.payment.client.dto.BankPaymentRequest;
import com.payments.payment_service.payment.client.dto.BankPaymentResponse;
import com.payments.payment_service.payment.client.dto.FailureReason;
import com.payments.payment_service.payment.client.dto.TransactionStatus;
import com.payments.payment_service.payment.entity.Payment;
import com.payments.payment_service.payment.entity.type.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessor {

    private final BankClient bankClient;

    /**
     *
     * Build the BankPaymentRequest body and sends an HTTP request to Mock Bank using the bankClient object
     *
     * @param payment object to be converted to request
     * @return Response from Mock bank Service
     */
    private BankPaymentResponse getBankProcessedResponse(Payment payment) {
        BankPaymentRequest request = BankPaymentRequest.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .build();

        return bankClient.process(request);
    }

    /**
     * Maps the Transaction Status received from Mock Bank Service into an equivalent Payment status
     *
     * @param bankPaymentResponse the response from Mock Bank service that contains the TransactionStatus
     * @param payment             the current Payment object whose PaymentStatus needs to be updated
     */
    private void mapBankResponseToPayment(BankPaymentResponse bankPaymentResponse, Payment payment) {
        if (bankPaymentResponse.getTransactionStatus() == TransactionStatus.APPROVED) {
            payment.setStatus(PaymentStatus.SUCCESS);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(bankPaymentResponse.getReason().name());
        }
    }

    /**
     *
     * Orchestration Function to call related functions to process the payment and update the status
     *
     * @param payment object that needs to be processes
     * @return Object of Payment class which is process and saved in DB
     */
    public Payment process(Payment payment) {
        BankPaymentResponse bankPaymentResponse;

        try {
            bankPaymentResponse = getBankProcessedResponse(payment);
            mapBankResponseToPayment(bankPaymentResponse, payment);
        } catch (Exception e) {
            log.warn("Bank processing Failed! Failing payment: {}", e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(FailureReason.BANK_SERVICE_UNAVAILABLE.name());
        }

        return payment;
    }

}
