package co.com.terpel.gazel.gateway.infrastructure.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Propaga X-Trace-Id (lo genera si no viene) y no toca X-Usuario-Id / X-Rol-Usuario-Id:
 * sin autenticación el gateway no debe inventar identidades para la auditoría.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER) != null) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_ID_HEADER, UUID.randomUUID().toString()))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
