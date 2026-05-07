package com.payments.payment_service.payment.controller;

import com.payments.payment_service.payment.dto.PaymentRequest;
import com.payments.payment_service.payment.dto.PaymentResponse;
import com.payments.payment_service.payment.entity.type.PaymentStatus;
import com.payments.payment_service.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
    @PostMapping
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

    @Operation(
            summary = "Fetch All Payments",
            description = "Gets a Paged response of payments present in the DB"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of Payments retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "invalid pagination/filter params"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/admin")
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(paymentService.getAllPayments(page, size));
    }


    @Operation(
            summary = "Fetch Payments by Id",
            description = "Gets a payments present in the DB by its Id"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully from DB by its Id"),
            @ApiResponse(responseCode = "400", description = "invalid params"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/admin/id/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @Operation(
            summary = "Fetch Payments by Payment Status",
            description = "Gets a payments present in the DB by its Payment Status"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully from DB by its Payment Status"),
            @ApiResponse(responseCode = "400", description = "invalid params"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/admin/status/{status}")
    public ResponseEntity<Page<PaymentResponse>> getPaymentByStatus(
            @PathVariable PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return ResponseEntity.ok(paymentService.getPaymentByStatus(status, pageable));
    }
}