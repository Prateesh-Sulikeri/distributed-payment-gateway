package com.payments.payment_service.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiError {
    @Schema(description = "Http Status code for the type of exception that occured", example = "404")
    private int status;

    @Schema(description = "Exception Message, details about the exception", example = "java.lang.ArithmeticException: / by zero")
    private String message;

    @Schema(description = "Request URI of where the exception occurred", example = "GET /payments/123")
    private String path;

    @Schema(description = "Timestamp of when an exception occurred", example = "2026-05-06T14:22:31.456789Z")
    private Instant timestamp;
}
