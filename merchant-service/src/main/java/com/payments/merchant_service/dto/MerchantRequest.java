package com.payments.merchant_service.dto;

import jakarta.validation.constraints.AssertTrue;
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

    @AssertTrue(message = "At least one field must be provided")
    public boolean isValid() {
        return name != null ||
                email != null ||
                webhookUrl != null;
    }
}
