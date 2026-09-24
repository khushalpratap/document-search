package com.document.search.gateway.config;

import com.document.search.common.TenantHeaders;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
    // Rate limits are per tenant; the per-route rates live in application.yml.
    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> Mono.justOrEmpty(
                exchange.getRequest().getHeaders().getFirst(TenantHeaders.TENANT_ID))
                .defaultIfEmpty("anonymous");
    }
}
