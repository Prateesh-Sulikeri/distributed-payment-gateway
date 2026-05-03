package com.payments.payment_service.payments.controller;

import com.payments.payment_service.payments.dto.PaymentRequest;
import com.payments.payment_service.payments.dto.PaymentResponse;
import com.payments.payment_service.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "APIs for managing payment operations")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Create a new payment",
            description = "Creates a payment using an idempotency key. If the same key is used multiple times, the same payment will be returned instead of creating duplicates."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment created or retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("")
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(
                    description = "Payment request payload",
                    required = true
            )
            @RequestBody @Valid PaymentRequest request,

            @Parameter(
                    description = "Idempotency key to prevent duplicate payments",
                    required = true,
                    example = "order_12345_txn_67890"
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity.ok(paymentService.createPayment(request, idempotencyKey));
    }
}
