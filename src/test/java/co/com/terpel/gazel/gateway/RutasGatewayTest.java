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

    private record Peticion(String path, String traceId, String userCiamId, String userId, String rolUserId) {
    }

    private static final BlockingQueue<Peticion> PETICIONES = new LinkedBlockingQueue<>();

    /** Arranca antes de que Spring cargue el contexto: @DynamicPropertySource necesita el puerto. */
    private static final HttpServer BACKEND = iniciarBackend();

    private static HttpServer iniciarBackend() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", exchange -> {
                String rawPath = exchange.getRequestURI().getRawPath();
                PETICIONES.add(new Peticion(
                        rawPath,
                        exchange.getRequestHeaders().getFirst("X-Trace-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Ciam-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-Rol-User-Id")));
                byte[] cuerpo;
                if (rawPath.startsWith("/ms-identidad/api/v1/usuarios/ciam/")) {
                    cuerpo = "{\"idUsuario\":42,\"idRol\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                } else {
                    cuerpo = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
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

    @Test
    void extraeSubDelTokenConsultaCiamYEnviaHeadersAlMicroservicio() {
        // Header: {"alg":"none"}, Payload: {"sub":"usuario-123","name":"Test"}
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"usuario-123\",\"name\":\"Test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String jwt = header + "." + payload + ".";

        cliente.get().uri("/ms-operaciones/api/v1/placas")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        // 1ra petición al backend: la consulta a ms-identidad por el id_ciam (sub)
        Peticion peticionCiam = esperarPeticion();
        assertThat(peticionCiam.path()).isEqualTo("/ms-identidad/api/v1/usuarios/ciam/usuario-123");

        // 2da petición al backend: la petición enrutada a ms-operaciones con los headers poblados
        Peticion peticionOperaciones = esperarPeticion();
        assertThat(peticionOperaciones.path()).isEqualTo("/ms-operaciones/api/v1/placas");
        assertThat(peticionOperaciones.userCiamId()).isEqualTo("usuario-123");
        assertThat(peticionOperaciones.userId()).isEqualTo("42");
        assertThat(peticionOperaciones.rolUserId()).isEqualTo("7");
    }

    @Test
    void noEnviaHeaderXUserIdCuandoNoHayToken() {
        cliente.get().uri("/ms-operaciones/api/v1/placas")
                .exchange()
                .expectStatus().isOk();

        Peticion recibida = esperarPeticion();
        assertThat(recibida.userCiamId()).isNull();
        assertThat(recibida.userId()).isNull();
        assertThat(recibida.rolUserId()).isNull();
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
