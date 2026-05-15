package com.payments.mock_bank_service.processor;

import com.payments.mock_bank_service.type.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentRouter {

    private final Map<PaymentMethod, PaymentProcessor> processorMap;

    public PaymentRouter(
            CardProcessor cardProcessor,
            UpiProcessor upiProcessor,
            NetBankingProcessor netBankingProcessor
    ){
        this.processorMap = Map.of(
                PaymentMethod.CARD, cardProcessor,
                PaymentMethod.UPI, upiProcessor,
                PaymentMethod.NET_BANKING, netBankingProcessor
        );
    }

    public PaymentProcessor getProcessor(PaymentMethod paymentMethod) {
        PaymentProcessor processor = processorMap.get(paymentMethod);

        if (processor == null) {
            throw new IllegalArgumentException("Unsupported Payment method" + paymentMethod);
        }
        return processor;
    }
}
