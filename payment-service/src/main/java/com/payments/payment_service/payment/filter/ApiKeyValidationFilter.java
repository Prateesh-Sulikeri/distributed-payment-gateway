package com.payments.payment_service.payment.filter;

import com.payments.payment_service.payment.client.MerchantServiceClient;
import com.payments.payment_service.payment.client.dto.MerchantResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyValidationFilter extends OncePerRequestFilter {

    private final MerchantServiceClient merchantServiceClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("Filter called for URI: {}", request.getRequestURI());
        if (!request.getRequestURI().startsWith("/payments")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = authHeader.substring(7);

        Optional<MerchantResponse> merchant = merchantServiceClient.validateApiKey(apiKey);

        if (merchant.isEmpty()) {
            response.setStatus(401);
            response.getWriter().write("Invalid API Key");
            return;
        }

        request.setAttribute("merchantId", merchant.get().getMerchantId().toString());
        log.info("Set merchantId in request: {}", merchant.get().getMerchantId().toString());
        request.setAttribute("merchant", merchant.get());
        log.info("Set merchant in request: {}", merchant.get());

        filterChain.doFilter(request, response);
    }
}
