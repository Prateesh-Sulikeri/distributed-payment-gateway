package com.payments.merchant_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MerchantRequest {
    private String name;
    private String email;
    private String webhookUrl;
}
