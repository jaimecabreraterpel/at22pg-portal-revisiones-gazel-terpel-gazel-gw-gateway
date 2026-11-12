package co.com.terpel.gazel.gateway.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rutas del portal. Todos los servicios quedan bajo {@code /ms-*} para que el front
 * use una sola base y ningún servicio nuevo necesite la raíz del gateway.
 */
@Configuration
public class RutasConfig {

    @Bean
    RouteLocator rutasGazel(RouteLocatorBuilder builder,
                            @Value("${gazel.gateway.uris.identidad}") String identidad,
                            @Value("${gazel.gateway.uris.operaciones}") String operaciones,
                            @Value("${gazel.gateway.uris.facturacion}") String facturacion,
                            @Value("${gazel.gateway.uris.parametros}") String parametros,
                            @Value("${gazel.gateway.uris.analitica}") String analitica,
                            @Value("${gazel.gateway.uris.soporte}") String soporte) {
        return builder.routes()
                // Sin strip: estos servicios ya se publican bajo su propio context-path.
                .route("identidad", r -> r.path("/ms-identidad/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(identidad))
                .route("operaciones", r -> r.path("/ms-operaciones/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(operaciones))
                .route("facturacion", r -> r.path("/ms-facturacion/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(facturacion))
                .route("parametros", r -> r.path("/ms-parametros/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(parametros))
                // ms-analitica no declara context-path: sirve en raíz, por eso se recorta /ms-analitica.
                .route("analitica", r -> r.path("/ms-analitica/**")
                        .filters(f -> f.stripPrefix(1)
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(analitica))
                // cf-soporte es un HttpFunction (Functions Framework) que responde en raiz:
                // sirve su health en cualquier path, asi que tambien se recorta el prefijo.
                .route("soporte", r -> r.path("/ms-soporte/**")
                        .filters(f -> f.stripPrefix(1)
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri(soporte))
                .build();
    }
}
