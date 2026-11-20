package co.com.terpel.gazel.gateway.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extrae el claim {@code sub} del token JWT enviado en {@code Authorization: Bearer <token>}
 * (sin validar la firma), consulta Identidad y sobrescribe los headers derivados del usuario
 * antes de enrutar la solicitud a los microservicios.
 */
@Component
public class JwtSubHeaderGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtSubHeaderGlobalFilter.class);

    public static final String X_USER_CIAM_ID_HEADER = "X-User-Ciam-Id";
    public static final String X_USER_ID_HEADER = "X-User-Id";
    public static final String X_ROL_USER_ID_HEADER = "X-Rol-User-Id";
    public static final String X_TALLER_ID_HEADER = "X-Taller-Id";
    public static final String X_CERTIFICADOR_ID_HEADER = "X-Certificador-Id";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CIAM_ENDPOINT_TEMPLATE = "/ms-identidad/api/v1/usuarios/ciam/%s";

    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();
    private final WebClient webClient;
    private final String identidadBaseUri;

    public JwtSubHeaderGlobalFilter(
            @Value("${gazel.gateway.uris.identidad}") String identidadBaseUri) {
        this.identidadBaseUri = identidadBaseUri.endsWith("/")
                ? identidadBaseUri.substring(0, identidadBaseUri.length() - 1)
                : identidadBaseUri;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Optional<String> subOpt = extractSubFromAuthorization(authHeader);
        if (subOpt.isEmpty()) {
            ServerHttpRequest mutated = mutateHeaders(exchange.getRequest(), null, Map.of());
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        String sub = subOpt.get();

        // Evita recursión infinita si la petición entrante ya es hacia el mismo endpoint de CIAM
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (rawPath != null && rawPath.startsWith("/ms-identidad/api/v1/usuarios/ciam/")) {
            ServerHttpRequest mutated = mutateHeaders(exchange.getRequest(), sub, Map.of());
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        String ciamUrl = identidadBaseUri + String.format(CIAM_ENDPOINT_TEMPLATE, sub);
        HttpHeaders ciamHeaders = new HttpHeaders();
        ciamHeaders.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null) {
            ciamHeaders.set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }

        log.info("Consulta a ms-identidad (Gateway -> Identidad):\n{}",
                CurlLoggingGlobalFilter.toCurl("GET", URI.create(ciamUrl), ciamHeaders));

        return webClient.get()
                .uri(ciamUrl)
                .headers(h -> h.addAll(ciamHeaders))
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJsonMap)
                .doOnNext(map -> log.info("Respuesta de ms-identidad para ciam '{}': {}", sub, map))
                .onErrorResume(ex -> {
                    log.warn("No fue posible consultar ms-identidad para ciam '{}': {}", sub, ex.getMessage());
                    return Mono.just(Map.of());
                })
                .defaultIfEmpty(Map.of())
                .flatMap(userData -> {
                    ServerHttpRequest mutated = mutateHeaders(exchange.getRequest(), sub, userData);
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private ServerHttpRequest mutateHeaders(ServerHttpRequest request, String sub, Map<String, Object> userData) {
        String usuarioId = extractField(userData, List.of("idUsuario", "usuarioId", "id"));
        String rolId = extractRolId(userData);
        String estado = extractField(userData, List.of("estado"));
        String nombreRol = extractField(userData, List.of("nombreRol"));
        String tallerId = "ACTIVO".equalsIgnoreCase(estado) && "TALLER".equalsIgnoreCase(nombreRol)
                ? extractField(userData, List.of("idTaller")) : null;
        String certificadorId = "ACTIVO".equalsIgnoreCase(estado) && "CERTIFICADOR".equalsIgnoreCase(nombreRol)
                ? extractField(userData, List.of("idCertificador")) : null;

        return request.mutate()
                .headers(headers -> {
                    headers.remove(X_USER_CIAM_ID_HEADER);
                    headers.remove(X_USER_ID_HEADER);
                    headers.remove(X_ROL_USER_ID_HEADER);
                    headers.remove(X_TALLER_ID_HEADER);
                    headers.remove(X_CERTIFICADOR_ID_HEADER);
                    if (sub != null) {
                        headers.set(X_USER_CIAM_ID_HEADER, sub);
                    }
                    if (usuarioId != null) {
                        headers.set(X_USER_ID_HEADER, usuarioId);
                    }
                    if (rolId != null) {
                        headers.set(X_ROL_USER_ID_HEADER, rolId);
                    }
                    if (tallerId != null) {
                        headers.set(X_TALLER_ID_HEADER, tallerId);
                    }
                    if (certificadorId != null) {
                        headers.set(X_CERTIFICADOR_ID_HEADER, certificadorId);
                    }
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = jsonParser.parseMap(body);
            // Si la respuesta viene envuelta en {"data": {...}}
            if (parsed.get("data") instanceof Map<?, ?> dataMap) {
                return (Map<String, Object>) dataMap;
            }
            return parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String extractField(Map<String, Object> map, List<String> candidateKeys) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String key : candidateKeys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractRolId(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String direct = extractField(map, List.of("idRolUsuario", "rolUsuarioId", "idRol", "rolId"));
        if (direct != null) {
            return direct;
        }
        if (map.get("rol") instanceof Map<?, ?> rolMap) {
            return extractField((Map<String, Object>) rolMap, List.of("idRol", "id", "idRolUsuario"));
        }
        if (map.get("roles") instanceof List<?> rolesList && !rolesList.isEmpty()) {
            Object first = rolesList.get(0);
            if (first instanceof Map<?, ?> firstRolMap) {
                return extractField((Map<String, Object>) firstRolMap, List.of("idRol", "id", "idRolUsuario"));
            }
        }
        return null;
    }

    private Optional<String> extractSubFromAuthorization(String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = jsonParser.parseMap(new String(decodedPayload, StandardCharsets.UTF_8));
            Object subObj = claims.get("sub");
            if (subObj != null) {
                String sub = subObj.toString();
                if (!sub.isBlank()) {
                    return Optional.of(sub);
                }
            }
        } catch (Exception ignored) {
            // Si el token no es un JWT decodificable, continúa sin agregar la cabecera
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
