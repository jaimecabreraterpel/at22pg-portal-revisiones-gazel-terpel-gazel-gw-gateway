# Gateway sin resolución de identidad: reenvío de headers

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El gateway deja de llamar a `ms-identidad` y de derivar headers desde el JWT; reenvía tal cual los headers de identidad que ya envía el cliente (`X-User-Id`, `X-Rol-User-Id`, `X-Taller-Id`, `X-Certificador-Id`, etc.).

**Architecture:** Spring Cloud Gateway reenvía por defecto todas las cabeceras entrantes al microservicio destino; no hace falta filtro. Se elimina `JwtSubHeaderGlobalFilter` (que hoy decodifica el `sub` y consulta `/ms-identidad/api/v1/usuarios/ciam/{sub}`), se conservan `TraceIdGlobalFilter`, `CurlLoggingGlobalFilter` y las rutas de `RutasConfig`.

**Tech Stack:** Java 21, Spring Boot 4.0.9, Spring Cloud Gateway (WebFlux), JUnit 5 + WebTestClient, `com.sun.net.httpserver` para el backend de prueba.

**Base:** rama nueva desde `master` (`6e1a767`). La rama `feature/gateway-headers-taller-certificador` (commit `c89db12`) queda obsoleta: su PR, si está abierto, se cierra.

---

## Advertencia de seguridad (leer antes de ejecutar)

Este diseño hace que **cualquiera que alcance el puerto del gateway pueda falsificar `X-User-Id`/`X-Rol-User-Id`/`X-Taller-Id`**. Hoy los microservicios confían en esos headers:

| Header | Consumidor | Evidencia |
|---|---|---|
| `X-Rol-User-Id` (+ alias `X-Rol-Id`) | `ms-operaciones` (`GestionServicioController`) | `GatewayRolScopeResolver`: 400 `"Falta el header X-Rol-User-Id"` |
| `X-Talleres-Autorizados`, `X-Regionales-Autorizadas`, `X-Talleres-Cartera` | `ms-operaciones` | `GatewayRolScopeResolver` (alcance ASISTENTE / VENTAS) |
| `X-Taller-Id` | `ms-operaciones` | `RequestContextTallerActualProvider` |
| `X-Certificador-Id` | `ms-operaciones` | `RequestContextCertificadorActualProvider` |
| `X-User-Id` | `ms-operaciones`, `ms-identidad`, `ms-facturacion` | `RequestContextUsuarioActualProvider`, `AuditoriaInterceptor`, `PreFacturaReporteController` |
| `X-User-Ciam-Id` | `ms-identidad` | `TraceIdFilter`, `RequestHeadersLoggingFilter` |

Consecuencia: el gateway **no debe exponerse a Internet**; la autenticación debe ocurrir antes (Ingress/WAF/BFF que firme o filtre) y el acceso directo a los microservicios debe estar cerrado. Si eso no existe todavía, este plan introduce una escalada de privilegios trivial (cambiar `X-Rol-User-Id`). Decisión del usuario; queda documentada en el README (Task 4).

---

## Contrato de headers (lo que debe enviar quien llama al gateway)

```http
X-User-Ciam-Id:   <sub del token CIAM>
X-User-Id:        <UUID de ms_identidad.usuarios>
X-Rol-User-Id:    <UUID del rol activo>
X-Taller-Id:      <UUID del taller>        (solo rol TALLER)
X-Certificador-Id:<UUID del certificador>  (solo rol CERTIFICADOR)
X-Talleres-Autorizados:    <uuid,uuid>     (solo alcance ASISTENTE)
X-Regionales-Autorizadas:  <uuid,uuid>     (solo alcance VENTAS)
X-Talleres-Cartera:        <uuid,uuid>     (solo alcance VENTAS)
```

`X-Trace-Id` lo sigue generando/propagando el gateway.

**Prerrequisito fuera de este repo:** hoy `at22pg-portal-revisiones-gazel-terpel-gazel-front-web` solo envía `X-User-Id` en `POST /liquidar` (`src/features/prefacturas/services/prefacturasService.ts:253`) y `Authorization` en el resto (`src/config/axios.ts:73`, `src/config/operacionesAxios.ts:81`). Sin el cambio del front (o del BFF/Ingress) los endpoints de gestión responderán 400 por falta de `X-Rol-User-Id`. No se planifica aquí.

---

## File Structure

| Archivo | Acción | Responsabilidad |
|---|---|---|
| `src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/JwtSubHeaderGlobalFilter.java` | Eliminar | Hoy decodifica el JWT y consulta Identidad |
| `src/test/java/co/com/terpel/gazel/gateway/RutasGatewayTest.java` | Modificar | Contrato: reenvío literal de headers y ausencia de llamadas a Identidad |
| `src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/CurlLoggingGlobalFilter.java` | Modificar (comentario) | Ya no existe el filtro de identidad detrás |
| `README.md` | Modificar | Sección "Cabeceras" + riesgo asumido |
| `docs/diseno-contexto-identidad-operativa.md` | Modificar | Marcar la resolución de identidad en el gateway como descartada |

No se toca `RutasConfig` ni `application.yml`: `gazel.gateway.uris.identidad` sigue siendo necesario para la ruta `/ms-identidad/**`.

---

### Task 1: Tests del contrato de reenvío

**Files:**
- Modify: `src/test/java/co/com/terpel/gazel/gateway/RutasGatewayTest.java`

- [ ] **Step 1: Reemplazar el archivo de test completo**

Contenido final (borra los dos tests de identidad, simplifica el backend de prueba y añade el contrato):

```java
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
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run (bash roto en este equipo por CRLF; usar PowerShell):

```bash
powershell -NoProfile -Command "\$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'; & 'C:\Users\jaime.cabrera.ext\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' -o -Dtest=RutasGatewayTest test"
```

Expected: FALLA **solo** `noConsultaMsIdentidadAunqueVengaAuthorization` con `expected: "/ms-operaciones/api/v1/placas" but was: "/ms-identidad/api/v1/usuarios/ciam/usuario-123"` (el filtro hace la segunda petición). `reenviaTalCualLosHeadersDeIdentidadDelCliente` **pasa** ya en rojo: sin `Authorization` con `sub`, `JwtSubHeaderGlobalFilter` no toca los headers y los reenvía tal cual; el test queda como guarda de regresión del reenvío.

- [ ] **Step 3: No commitear todavía.** La implementación va en Task 2 con el test ya en rojo.

---

### Task 2: Eliminar el filtro que resuelve identidad

**Files:**
- Delete: `src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/JwtSubHeaderGlobalFilter.java`

- [ ] **Step 1: Borrar el filtro**

```bash
git rm src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/JwtSubHeaderGlobalFilter.java
```

Único consumidor de `WebClient` y del endpoint `/ms-identidad/api/v1/usuarios/ciam/{sub}` en el gateway; no queda ninguna referencia (`grep -rn "JwtSubHeaderGlobalFilter" src/main` sin resultados).

- [ ] **Step 2: Ejecutar toda la suite**

```bash
powershell -NoProfile -Command "\$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'; & 'C:\Users\jaime.cabrera.ext\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' -o test"
```

Expected: `BUILD SUCCESS`. `RutasGatewayTest`: `Tests run: 10`. Total del módulo: `Tests run: 12` (10 de rutas + 2 de `GatewayApplicationTests`), 0 fallos.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/co/com/terpel/gazel/gateway/RutasGatewayTest.java
git commit -m "refactor(gateway): reenviar headers de identidad sin consultar ms-identidad"
```

---

### Task 3: Comentario del filtro de logging

**Files:**
- Modify: `src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/CurlLoggingGlobalFilter.java:45`

- [ ] **Step 1: Corregir el comentario de orden**

Reemplazar:

```java
            // Antes de TraceIdGlobalFilter (+10) y JwtSubHeaderGlobalFilter (+20) para mostrar lo que envió el cliente originalmente
            return Ordered.HIGHEST_PRECEDENCE;
```

por:

```java
            // Antes de TraceIdGlobalFilter (+10) para mostrar lo que envió el cliente originalmente
            return Ordered.HIGHEST_PRECEDENCE;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/CurlLoggingGlobalFilter.java
git commit -m "docs(gateway): actualizar comentario de orden tras eliminar el filtro de identidad"
```

---

### Task 4: README

**Files:**
- Modify: `README.md` (sección "Cabeceras", líneas ~91-97)

- [ ] **Step 1: Reemplazar la sección "Cabeceras"**

De:

```markdown
## Cabeceras

- `X-Trace-Id`: si el cliente no la envía, el gateway la genera (UUID) y la propaga.
- `X-Usuario-Id`, `X-Rol-Usuario-Id`, `X-Zona`: se reenvían **tal cual**. El gateway no las
  inventa. Mientras no exista autenticación, las descargas de `ms-analitica`
  (`/api/v1/indicadores/**/descargar`) responden 400 porque esos endpoints las exigen.
```

a:

```markdown
## Cabeceras

- `X-Trace-Id`: si el cliente no la envía, el gateway la genera (UUID) y la propaga.
- El gateway **no autentica ni consulta Identidad**. Reenvía tal cual los headers de identidad
  que envía el cliente: `X-User-Ciam-Id`, `X-User-Id`, `X-Rol-User-Id`, `X-Taller-Id`,
  `X-Certificador-Id`, `X-Talleres-Autorizados`, `X-Regionales-Autorizadas`,
  `X-Talleres-Cartera`.
- Riesgo asumido: cualquiera que alcance el puerto del gateway puede falsificar esos headers y
  `ms-operaciones` los usa para alcance por rol y para resolver taller/certificador. No expongas
  el gateway directamente a Internet: la autenticación debe resolverse antes (Ingress/WAF/BFF) y
  el acceso directo a los microservicios debe estar cerrado.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs(gateway): documentar reenvío de headers de identidad y su riesgo"
```

---

### Task 5: Marcar la decisión descartada en el documento de diseño

**Files:**
- Modify: `docs/diseno-contexto-identidad-operativa.md`

- [ ] **Step 1: Reemplazar la sección "Diferencias del codigo actual"**

De:

```markdown
## Diferencias del codigo actual

- El filtro `JwtSubHeaderGlobalFilter` del gateway decodifica `sub` sin verificar la firma del JWT.
- Cuando la respuesta de Identidad contiene `roles` como lista, el filtro actual toma el primer elemento; eso no demuestra que sea el rol activo.
- El filtro actual obtiene `X-User-Id` y `X-Rol-User-Id` consultando Identidad, pero no los limpia/sobrescribe de forma incondicional cuando faltan valores.
```

a:

```markdown
## Diferencias del codigo actual

- **Descartado (2026-11-25):** el gateway ya no resuelve identidad. Se eliminó `JwtSubHeaderGlobalFilter`; el cliente envía `X-User-Ciam-Id`, `X-User-Id`, `X-Rol-User-Id`, `X-Taller-Id`, `X-Certificador-Id` y los headers de alcance, y el gateway los reenvía sin tocarlos. La autenticación y la derivación de esos headers deben ocurrir antes del gateway (Ingress/WAF/BFF), que es la condición para que el diseño siga siendo seguro.
```

- [ ] **Step 2: Añadir la fila al "Registro de decisiones"**

Añadir como última fila de la tabla:

```markdown
| (2026-11-25) El gateway reenvía headers de identidad tal cual y no consulta Identidad. | Mantener la resolución en el gateway (`JwtSubHeaderGlobalFilter`); firmar los headers en un BFF. | Se mueve la responsabilidad de autenticación fuera del gateway; el filtro duplicaba la lógica de Identidad en cada request. Riesgo aceptado: el gateway no debe quedar expuesto. |
```

- [ ] **Step 3: Commit**

```bash
git add docs/diseno-contexto-identidad-operativa.md
git commit -m "docs(gateway): registrar que el reenvío de headers reemplaza la resolución de identidad"
```

---

### Task 6: Verificación end-to-end

- [ ] **Step 1: Reiniciar el gateway con el cambio**

Parar la instancia actual del puerto 8090 y arrancar de nuevo (en este equipo el proceso es `java ... OperacionApplication`; en el gateway: `spring-boot:run` o el IDE).

- [ ] **Step 2: Probar que el header llega al microservicio**

Con `ms-operaciones` en `:8181` (context-path `/ms-operaciones`):

```bash
# 1) con rol administrador: debe responder 200
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'X-Rol-User-Id: b1000001-0000-0000-0000-000000000001' \
  'http://localhost:8090/ms-operaciones/api/v1/gestion/servicios/consulta'

# 2) sin el header: debe responder 400 (Falta el header X-Rol-User-Id)
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8090/ms-operaciones/api/v1/gestion/servicios/consulta'
```

Expected: `200` y `400` respectivamente. Si el segundo devuelve 500, `ms-operaciones` no está levantado.

- [ ] **Step 3: Confirmar en el log del gateway que no hay salida a Identidad**

En el log del gateway, `Petición de salida (Gateway -> Microservicio)` debe aparecer **una sola vez** por request y **nunca** con path `/ms-identidad/api/v1/usuarios/ciam/**`.

- [ ] **Step 4: Push**

```bash
git push -u origin HEAD
```

---

## Riesgos residuales

1. **Suplantación de identidad** (ver advertencia inicial): sin control de acceso previo, cualquier cliente define su rol y su taller. Mitigaciones, en orden de esfuerzo: cerrar el acceso directo a los microservicios, autenticar en el Ingress, o firmar los headers en un BFF.
2. **Front incompleto**: hoy solo envía `X-User-Id` en `POST /liquidar`. Hasta que envíe `X-Rol-User-Id` y los headers de alcance, `/api/v1/gestion/**` responde 400.
3. **Documentación de `ms-operaciones`**: `RequestContextUsuarioActualProvider` menciona `JwtSubHeaderGlobalFilter` en su javadoc (`ms-operaciones/.../infrastructure/security/RequestContextUsuarioActualProvider.java:12`). Fuera de alcance; se corrige cuando se toque ese repo.
4. **Autorización**: el gateway sigue sin validar firma de JWT ni roles. Con este cambio ni siquiera decodifica el token; `Authorization` se reenvía y cada microservicio decide qué hacer con él.
