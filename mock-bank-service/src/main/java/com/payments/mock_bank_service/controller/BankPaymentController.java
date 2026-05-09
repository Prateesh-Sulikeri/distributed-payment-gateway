package com.payments.mock_bank_service.controller;

import com.payments.mock_bank_service.MockBankServiceApplication;
import com.payments.mock_bank_service.dto.BankPaymentRequest;
import com.payments.mock_bank_service.dto.BankPaymentResponse;
import com.payments.mock_bank_service.service.BankPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bank")
@RequiredArgsConstructor
@Tag(name = "Mock Bank", description = "Simulatest bank processing with configurable success ratio and latency")
public class BankPaymentController {

    private final BankPaymentService service;

    @Operation(
            summary = "Process payment",
            description = "Simulates bank processing with random approval/decline and configurable latency"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/process")
    public ResponseEntity<BankPaymentResponse> process(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Bank payment request", required = true
            )
            @Valid @RequestBody BankPaymentRequest request
    ) {
        return ResponseEntity.ok(service.process(request));
    }
}
