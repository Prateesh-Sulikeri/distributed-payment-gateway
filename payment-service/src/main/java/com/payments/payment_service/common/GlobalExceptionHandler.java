package com.payments.payment_service.common;

import com.payments.payment_service.payment.entity.type.CurrencyCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request

    ) {
        return new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiError handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        return new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request body: Invalid Currency Code detected, Rejecting payment request. Supported Currencies: " + Arrays.stream(CurrencyCode.values()).map(Enum::name).toList(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(Exception.class)
    public ApiError handleException(
            Exception e,
            HttpServletRequest request
    ) {
        return new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                e.getMessage(),
                request.getRequestURI(),
                Instant.now()
        );
    }
}
