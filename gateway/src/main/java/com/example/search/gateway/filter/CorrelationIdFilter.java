package com.example.search.gateway.filter;

import com.example.search.common.TenantHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(TenantHeaders.CORRELATION_ID);
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        ServerWebExchange mutated = exchange.mutate().request(
                exchange.getRequest().mutate().header(TenantHeaders.CORRELATION_ID, id).build()
        ).build();
        mutated.getResponse().getHeaders().set(TenantHeaders.CORRELATION_ID, id);
        return chain.filter(mutated);
    }

    @Override public int getOrder() { return -100; }
}
