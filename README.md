# gazel-gw-gateway

API Gateway del Portal de Revisiones Gazel (Terpel). Spring Cloud Gateway (WebFlux) que expone
los microservicios backend bajo un único puerto y el prefijo `/ms-*`.

> **Herramienta de desarrollo local.** Sirve para golpear los microservicios con `curl` desde un
> solo puerto. **No se despliega**: en QA y PRD el front consume los microservicios directamente
> a través del ingress de GKE, y este proyecto no tiene Dockerfile ni pipeline (a diferencia de
> los `ms-*`). Si algún día se despliega, falta lo listado en *Pendiente*.

## Requisitos

- **JDK 21** (el `pom.xml` compila con `release 21`; con 17 o 11 falla).
- Conexión a internet en el primer build: `mvnw` descarga Maven y las dependencias de Spring.

No hay base de datos, credenciales ni variables obligatorias. Verifica el JDK antes de empezar:

```bash
java -version        # debe decir 21.x
echo $JAVA_HOME      # en Windows: echo %JAVA_HOME%
```

Si el sistema tiene otra versión por defecto, exporta un JDK 21 solo para esta terminal:

```bash
export JAVA_HOME="/c/Program Files/Zulu/zulu-21"     # Git Bash
# PowerShell: $env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"
```

Sin `JAVA_HOME` correcto el build falla con `release version 21 not supported`.

## Arrancar

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
# http://localhost:8090/actuator/health
```

El gateway **no levanta los microservicios**: si no están arriba, cada ruta responde 500 con
`Connection refused`. Para que enrute de verdad hay que arrancar cada repo `ms-*` (o el script
`arrancar-todo.sh` que está fuera de este repo).

## Rutas

| Ruta pública        | Destino          | Variable de entorno | Default local |
| :------------------ | :--------------- | :------------------ | :------------ |
| `/ms-identidad/**`   | ms-identidad     | `MS_IDENTIDAD_URI`   | `http://localhost:8180` |
| `/ms-operaciones/**` | ms-operaciones   | `MS_OPERACIONES_URI` | `http://localhost:8181` |
| `/ms-facturacion/**` | ms-facturacion   | `MS_FACTURACION_URI` | `http://localhost:8182` |
| `/ms-parametros/**`  | ms-parametros    | `MS_PARAMETROS_URI`  | `http://localhost:8183` |
| `/ms-analitica/**`   | ms-analitica     | `MS_ANALITICA_URI`   | `http://localhost:8184` |
| `/ms-soporte/**`     | cf-soporte       | `MS_SOPORTE_URI`     | `http://localhost:8080` |

- Los cuatro primeros **no** recortan prefijo: el microservicio ya declara ese `context-path`.
  La URL pública es el prefijo del gateway + el path del controller.
- `ms-analitica` y `cf-soporte` **sí** recortan (`StripPrefix=1`): sirven en raíz, sin
  `context-path`.
- Cada servicio usa un puerto local distinto, así que se pueden levantar todos a la vez.

### Ejemplos

```bash
# talleres (ms-operaciones declara context-path /ms-operaciones)
curl "http://localhost:8090/ms-operaciones/api/v1/talleres?page=0&size=20"
curl "http://localhost:8090/ms-operaciones/api/v1/talleres/{id}"

# analitica pierde el prefijo: se pide /ms-analitica/api/v1/... y llega /api/v1/...
curl "http://localhost:8090/ms-analitica/api/v1/indicadores"

# soporte: health de la Cloud Function
curl "http://localhost:8090/ms-soporte/"
```

Para apuntar una ruta a otro lado:

```bash
MS_FACTURACION_URI=http://localhost:9002 ./mvnw spring-boot:run
```

### cf-soporte (Cloud Function)

No es un microservicio Spring: es `at22pg-portal-revisiones-gazel-terpel-gazel-cf-soporte`, una
Cloud Function Gen 2. Se levanta con `./mvnw function:run` (desde su repo).

`-Drun.port=...` **no** cambia el puerto: su `pom.xml` fija `<port>8080</port>` en el
`function-maven-plugin`. Si el 8080 está ocupado, edita esa línea y alinea `MS_SOPORTE_URI`.

Solo expone un health estático. **No hay endpoint de consulta de auditorías**: la función
consume eventos de Pub/Sub y los persiste; leerlos requeriría un endpoint nuevo en ese repo.

## Cabeceras

- `X-Trace-Id`: si el cliente no la envía, el gateway la genera (UUID) y la propaga.
- Con `Authorization: Bearer`, el gateway decodifica el `sub`, consulta Identidad y sobrescribe los headers `X-User-*` con los valores de la respuesta. Propaga `X-Taller-Id` solo para usuario `ACTIVO` con rol `TALLER`, y `X-Certificador-Id` solo para `ACTIVO` con rol `CERTIFICADOR`. Si no puede decodificar el JWT o no resuelve la identidad, elimina esos headers recibidos del cliente.
- El gateway solo decodifica el JWT; no valida su firma. No expongas Operaciones directamente si confía en `X-Taller-Id`.

## CORS

Centralizado en el gateway (`CorsConfig`), con `GATEWAY_CORS_ALLOWED_ORIGINS` (default
`http://localhost:5173`). Las rutas deduplican `Access-Control-Allow-Origin` para no duplicar la
cabecera cuando el microservicio destino también la envía.

## Configuración

Todas las variables tienen default; no hace falta exportar ninguna en local.

| Variable | Default | Uso |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8090` | Puerto del gateway. **No se lee `PORT`.** |
| `MS_*_URI` | `http://localhost:818x` | Destino de cada ruta |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info` | Endpoints de actuator |

## Tests

```bash
./mvnw test
```

- `RutasGatewayTest`: levanta un backend HTTP de prueba y verifica el path con el que llega
  cada ruta (incluido el `StripPrefix` de analítica y soporte) y la propagación de `X-Trace-Id`.
- `GatewayApplicationTests`: comprueba que existan las seis rutas y sus URIs por defecto.

## Stack

- Java 21, Spring Boot 4.0.9, Spring Cloud 2025.1.4 (release train GA más reciente).
  Es un deployable independiente: no necesita igualar el Boot 4.1.x de los microservicios.

## Pendiente

Solo aplica **si el gateway llega a desplegarse**; hoy no hace falta para uso local.

- Autenticación: hoy es pass-through sin validar token (decisión explícita). Sin auth, el
  gateway reenvía `X-Usuario-Id` / `X-Rol-Usuario-Id` / `X-Zona` tal cual, así que un cliente
  puede elegir su rol: aceptable en local, no en un entorno compartido.
- Pipeline CI (`azure-pipelines.yml`), Dockerfile y timeouts hacia los backends.
- Agregación de OpenAPI.
