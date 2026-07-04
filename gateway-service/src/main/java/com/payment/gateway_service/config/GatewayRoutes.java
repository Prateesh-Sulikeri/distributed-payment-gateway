package com.payment.gateway_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class GatewayRoutes {

    private final String paymentServiceUrl;
    private final String merchantServiceUrl;
    private final String webhookServiceUrl;
    private final String settlementServiceUrl;

    public GatewayRoutes(
            @Value("${payment-service.url}") String paymentServiceUrl,
            @Value("${merchant-service.url}") String merchantServiceUrl,
            @Value("${webhook-service.url}") String webhookServiceUrl,
            @Value("${settlement-service.url}") String settlementServiceUrl) {
        this.paymentServiceUrl = paymentServiceUrl;
        this.merchantServiceUrl = merchantServiceUrl;
        this.webhookServiceUrl = webhookServiceUrl;
        this.settlementServiceUrl = settlementServiceUrl;
    }

    @Bean
    RouterFunction<ServerResponse> gatewayRouterFunctions() {

        return route("payment-service")
                .GET("/payments/**", http())
                .POST("/payments/**", http())
                .before(uri(paymentServiceUrl))
                .build()
                .and(route("merchant-service")
                        .GET("/merchants/**", http())
                        .POST("/merchants/**", http())
                        .before(uri(merchantServiceUrl))
                        .build())

                .and(route("webhook-service")
                        .GET("/webhooks/**", http())
                        .POST("/webhooks/**", http())
                        .before(uri(webhookServiceUrl))
                        .build())

                .and(route("settlement-service")
                        .GET("/settlements/**", http())
                        .POST("/settlements/**", http())
                        .before(uri(settlementServiceUrl))
                        .build());
    }
}
