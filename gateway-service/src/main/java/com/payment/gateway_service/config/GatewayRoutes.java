package com.payment.gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class GatewayRoutes {

    @Bean
    RouterFunction<ServerResponse> gatewayRouterFunctions() {

        return route("payment-service")
                .GET("/payments/**", http())
                .POST("/payments/**", http())
                .before(uri("http://localhost:8080"))
                .build()
                .and(route("merchant-service")
                        .GET("/merchants/**", http())
                        .POST("/merchants/**", http())
                        .before(uri("http://localhost:8082"))
                        .build())

                .and(route("webhook-service")
                        .GET("/webhooks/**", http())
                        .POST("/webhooks/**", http())
                        .before(uri("http://localhost:8083"))
                        .build())

                .and(route("settlement-service")
                        .GET("/settlements/**", http())
                        .POST("/settlements/**", http())
                        .before(uri("http://localhost:8084"))
                        .build());
    }
}
