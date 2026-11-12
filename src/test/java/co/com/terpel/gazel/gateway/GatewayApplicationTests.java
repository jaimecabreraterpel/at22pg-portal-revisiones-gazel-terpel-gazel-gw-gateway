package co.com.terpel.gazel.gateway;

import co.com.terpel.gazel.gateway.infrastructure.config.TraceIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void exponeUnaRutaPorServicioBajoMs() {
        Map<String, Route> rutas = routeLocator.getRoutes().collectList().block().stream()
                .collect(Collectors.toMap(Route::getId, r -> r));

        assertThat(rutas).containsOnlyKeys(
                "identidad", "operaciones", "facturacion", "parametros", "analitica", "soporte");
        assertThat(rutas.get("identidad").getUri()).hasToString("http://localhost:8180");
        assertThat(rutas.get("analitica").getUri()).hasToString("http://localhost:8184");
        assertThat(rutas.get("soporte").getUri()).hasToString("http://localhost:8080");
    }

    @Test
    void elFiltroDeTraceIdEstaRegistrado() {
        assertThat(TraceIdGlobalFilter.TRACE_ID_HEADER).isEqualTo("X-Trace-Id");
    }
}
