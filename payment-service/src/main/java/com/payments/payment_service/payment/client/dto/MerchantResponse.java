package com.payments.payment_service.payment.client.dto;

import com.payments.payment_service.common.type.MerchantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MerchantResponse {
    private UUID merchantId;
    private String name;
    private String email;
    private String apiKey;
    private String webhookUrl;
    private Instant createdAt;
    private MerchantStatus status;
}
