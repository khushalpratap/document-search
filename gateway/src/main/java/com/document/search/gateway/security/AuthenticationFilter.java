package com.document.search.gateway.security;

import com.document.search.common.TenantHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter implements GlobalFilter {

    @Value("${gateway.auth.api-key:assessment-key}")
    private String configuredApiKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator/health") || path.equals("/health")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(TenantHeaders.API_KEY);
        String tenantId = exchange.getRequest().getHeaders().getFirst(TenantHeaders.TENANT_ID);

        if (!configuredApiKey.equals(apiKey) || tenantId == null || tenantId.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(TenantHeaders.TENANT_ID, tenantId)
                        .build())
                .build());
    }
}
