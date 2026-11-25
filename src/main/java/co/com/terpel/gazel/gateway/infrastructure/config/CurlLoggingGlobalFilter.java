package co.com.terpel.gazel.gateway.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashSet;

/**
 * Registra en los logs la petición de entrada al Gateway (antes de mutar headers)
 * y la petición de salida hacia el microservicio destino (con {@code X_USER_ID}, {@code X-Trace-Id}
 * y la URL final reescrita) en formato {@code curl}.
 */
public class CurlLoggingGlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(CurlLoggingGlobalFilter.class);

    @Component
    public static class IncomingCurlLogFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            LinkedHashSet<URI> originalUris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
            URI uri = (originalUris != null && !originalUris.isEmpty())
                    ? originalUris.iterator().next()
                    : request.getURI();

            log.info("Petición de entrada (Cliente -> Gateway):\n{}",
                    toCurl(request.getMethod().name(), uri, request.getHeaders()));
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            // Antes de TraceIdGlobalFilter (+10) para mostrar lo que envió el cliente originalmente
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    @Component
    public static class OutgoingCurlLogFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            URI targetUri = exchange.getAttributeOrDefault(
                    ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, request.getURI());

            log.info("Petición de salida (Gateway -> Microservicio):\n{}",
                    toCurl(request.getMethod().name(), targetUri, request.getHeaders()));
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            // Después de RouteToRequestUrlFilter (10000) para tener la URL final hacia el microservicio y los headers inyectados
            return 10500;
        }
    }

    static String toCurl(String method, URI uri, HttpHeaders headers) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method).append(" \"").append(uri).append("\"");
        headers.forEach((name, values) -> {
            for (String value : values) {
                sb.append(" \\\n  -H \"").append(name).append(": ").append(value).append("\"");
            }
        });
        return sb.toString();
    }
}
