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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo del enrutamiento real: levanta un backend HTTP de prueba y verifica
 * que el gateway reescriba el path como espera cada microservicio y que reenvíe
 * los headers de identidad sin consultar ms-identidad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasGatewayTest {

    private record Peticion(
            String path,
            String traceId,
            String userCiamId,
            String userId,
            String rolUserId,
            String tallerId,
            String certificadorId,
            String talleresAutorizados,
            String regionalesAutorizadas,
            String talleresCartera
    ) {
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
                        exchange.getRequestHeaders().getFirst("X-Trace-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Ciam-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-Rol-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-Taller-Id"),
                        exchange.getRequestHeaders().getFirst("X-Certificador-Id"),
                        exchange.getRequestHeaders().getFirst("X-Talleres-Autorizados"),
                        exchange.getRequestHeaders().getFirst("X-Regionales-Autorizadas"),
                        exchange.getRequestHeaders().getFirst("X-Talleres-Cartera")));
                byte[] cuerpo = "ok".getBytes(StandardCharsets.UTF_8);
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
    void reenviaTalCualLosHeadersDeIdentidadDelCliente() {
        cliente.get().uri("/ms-operaciones/api/v1/taller/servicios/consulta")
                .header("X-User-Ciam-Id", "ciam-123")
                .header("X-User-Id", "usuario-42")
                .header("X-Rol-User-Id", "rol-7")
                .header("X-Taller-Id", "taller-42")
                .header("X-Certificador-Id", "certificador-42")
                .header("X-Talleres-Autorizados", "taller-1,taller-2")
                .header("X-Regionales-Autorizadas", "regional-1")
                .header("X-Talleres-Cartera", "taller-3")
                .exchange()
                .expectStatus().isOk();

        Peticion recibida = esperarPeticion();
        assertThat(recibida.path()).isEqualTo("/ms-operaciones/api/v1/taller/servicios/consulta");
        assertThat(recibida.userCiamId()).isEqualTo("ciam-123");
        assertThat(recibida.userId()).isEqualTo("usuario-42");
        assertThat(recibida.rolUserId()).isEqualTo("rol-7");
        assertThat(recibida.tallerId()).isEqualTo("taller-42");
        assertThat(recibida.certificadorId()).isEqualTo("certificador-42");
        assertThat(recibida.talleresAutorizados()).isEqualTo("taller-1,taller-2");
        assertThat(recibida.regionalesAutorizadas()).isEqualTo("regional-1");
        assertThat(recibida.talleresCartera()).isEqualTo("taller-3");
    }

    @Test
    void noConsultaMsIdentidadAunqueVengaAuthorization() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"usuario-123\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = header + "." + payload + ".";

        cliente.get().uri("/ms-operaciones/api/v1/placas")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        Peticion recibida = esperarPeticion();
        assertThat(recibida.path()).isEqualTo("/ms-operaciones/api/v1/placas");
        assertThat(recibida.userCiamId()).isNull();
        assertThat(recibida.userId()).isNull();
        assertThat(recibida.rolUserId()).isNull();
        assertThat(PETICIONES.poll())
                .as("el gateway no debe consultar /ms-identidad/api/v1/usuarios/ciam/**")
                .isNull();
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
