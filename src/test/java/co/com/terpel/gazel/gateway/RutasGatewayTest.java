package co.com.terpel.gazel.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo del enrutamiento real: levanta un backend HTTP de prueba y verifica
 * que el gateway reescriba el path como espera cada microservicio.
 *
 * <p>Es el caso que los tests de {@code RouteLocator} no cubren: un cambio de
 * {@code context-path} en un microservicio se detecta aquí, no en un curl manual.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasGatewayTest {

    private record Peticion(String path, String traceId) {
    }

    private static final BlockingQueue<Peticion> PETICIONES = new LinkedBlockingQueue<>();

    /** Arranca antes de que Spring cargue el contexto: @DynamicPropertySource necesita el puerto. */
    private static final HttpServer BACKEND = iniciarBackend();

    private static HttpServer iniciarBackend() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", exchange -> {
                PETICIONES.add(new Peticion(
                        exchange.getRequestURI().getRawPath(),
                        exchange.getRequestHeaders().getFirst("X-Trace-Id")));
                byte[] cuerpo = "ok".getBytes();
                exchange.sendResponseHeaders(200, cuerpo.length);
                try (var salida = exchange.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void apuntarTodasLasRutasAlBackend(DynamicPropertyRegistry registro) {
        String uri = "http://localhost:" + BACKEND.getAddress().getPort();
        registro.add("gazel.gateway.uris.identidad", () -> uri);
        registro.add("gazel.gateway.uris.operaciones", () -> uri);
        registro.add("gazel.gateway.uris.facturacion", () -> uri);
        registro.add("gazel.gateway.uris.parametros", () -> uri);
        registro.add("gazel.gateway.uris.analitica", () -> uri);
        registro.add("gazel.gateway.uris.soporte", () -> uri);
    }

    @Value("${local.server.port}")
    private int puerto;

    private WebTestClient cliente;

    @BeforeEach
    void prepararCliente() {
        PETICIONES.clear();
        cliente = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + puerto)
                .build();
    }

    @AfterAll
    static void detenerBackend() {
        if (BACKEND != null) {
            BACKEND.stop(0);
        }
    }

    /**
     * Cada ruta llega al backend con el path que el microservicio espera: los que
     * declaran {@code context-path} lo conservan; analítica y soporte lo pierden por stripPrefix.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/ms-identidad/api/v1/certificadores,   /ms-identidad/api/v1/certificadores",
            "/ms-operaciones/api/v1/placas,         /ms-operaciones/api/v1/placas",
            "/ms-facturacion/api/v1/facturas,       /ms-facturacion/api/v1/facturas",
            "/ms-parametros/api/v1/catalogos,       /ms-parametros/api/v1/catalogos",
            "/ms-analitica/api/v1/indicadores,      /api/v1/indicadores",
            "/ms-soporte/,                          /"
    })
    void enrutaConElPathQueEsperaElServicio(String rutaPublica, String pathEnBackend) {
        cliente.get().uri(rutaPublica).exchange().expectStatus().isOk();

        Peticion recibida = esperarPeticion();
        assertThat(recibida.path()).isEqualTo(pathEnBackend);
    }

    @Test
    void generaTraceIdCuandoElClienteNoLoEnvia() {
        cliente.get().uri("/ms-operaciones/api/v1/placas").exchange().expectStatus().isOk();

        String traceId = esperarPeticion().traceId();
        assertThat(traceId).isNotBlank();
        assertThat(UUID.fromString(traceId).toString()).isEqualTo(traceId);
    }

    @Test
    void preservaElTraceIdQueEnviaElCliente() {
        String traza = "traza-de-prueba-123";

        cliente.get().uri("/ms-parametros/api/v1/catalogos")
                .header("X-Trace-Id", traza)
                .exchange()
                .expectStatus().isOk();

        assertThat(esperarPeticion().traceId()).isEqualTo(traza);
    }

    private static Peticion esperarPeticion() {
        try {
            Peticion recibida = PETICIONES.poll(5, TimeUnit.SECONDS);
            assertThat(recibida).as("el backend no recibió la petición").isNotNull();
            return recibida;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrumpido esperando la petición", e);
        }
    }
}
