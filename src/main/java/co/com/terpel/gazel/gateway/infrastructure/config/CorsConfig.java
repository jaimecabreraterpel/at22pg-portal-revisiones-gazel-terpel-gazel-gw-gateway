package co.com.terpel.gazel.gateway.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS centralizado: los microservicios no lo declaran de forma homogénea.
 * Las rutas deduplican Access-Control-Allow-Origin para no duplicar la cabecera
 * cuando el servicio destino también la envía.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsWebFilter corsWebFilter(@Value("${gazel.gateway.cors.allowed-origins}") List<String> origenes) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOriginPatterns(origenes);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setExposedHeaders(List.of("Location", "Content-Disposition", "X-Trace-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuracion);
        return new CorsWebFilter(source);
    }
}
