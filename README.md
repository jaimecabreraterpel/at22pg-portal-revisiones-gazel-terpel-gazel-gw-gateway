# gazel-gw-gateway

API Gateway del Portal de Revisiones Gazel (Terpel). Spring Cloud Gateway (WebFlux) que expone
los cinco microservicios backend bajo un único puerto y el prefijo `/ms-*`.

> **Herramienta de desarrollo local.** Sirve para golpear los cinco microservicios con `curl`
> desde un solo puerto. **No se despliega**: en QA y PRD el front consume los microservicios
> directamente a través del ingress de GKE, y este proyecto no tiene Dockerfile ni pipeline
> (a diferencia de los `ms-*`). Si algún día se despliega, falta lo listado en *Pendiente*.

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
- `ms-analitica` **sí** recorta (`StripPrefix=1`), porque no declara `context-path` y sirve en raíz.
- `cf-soporte` también recorta: es una Cloud Function (`HttpFunction`) que sirve en raíz y
  responde su health en cualquier path.
- Cada servicio usa un puerto local distinto, así que se pueden levantar todos a la vez.

### cf-soporte (Cloud Function)

No es un microservicio Spring: es `at22pg-portal-revisiones-gazel-terpel-gazel-cf-soporte`, una
Cloud Function Gen 2. Se levanta con `./mvnw function:run` (desde su repo).

`-Drun.port=...` **no** cambia el puerto: su `pom.xml` fija `<port>8080</port>` en el
`function-maven-plugin`. Si el 8080 está ocupado, edita esa línea y alinea `MS_SOPORTE_URI`.

## Cabeceras

- `X-Trace-Id`: si el cliente no la envía, el gateway la genera (UUID) y la propaga.
- `X-Usuario-Id`, `X-Rol-Usuario-Id`, `X-Zona`: se reenvían **tal cual**. El gateway no las inventa.
  Mientras no exista autenticación, las descargas de `ms-analitica` (`/api/v1/indicadores/**/descargar`)
  responden 400 porque esos endpoints las exigen.

## CORS

Centralizado en el gateway (`CorsConfig`), con `GATEWAY_CORS_ALLOWED_ORIGINS` (default
`http://localhost:5173`). Las rutas deduplican `Access-Control-Allow-Origin` para no duplicar la
cabecera cuando el microservicio destino también la envía.

## Ejecutar en local

```bash
./mvnw spring-boot:run
# http://localhost:8090/actuator/health
```

Puerto: solo `SERVER_PORT` (default **8090**; en esta máquina 8080/8081 están ocupados y
8180-8184 son de los microservicios). No se lee `PORT`.

El `JAVA_HOME` del sistema en esta máquina es **Zulu 11** y el proyecto exige Java 21: si
`./mvnw` reporta la 11, exporta `JAVA_HOME` a un JDK 21 antes de compilar.

No hace falta exportar ninguna variable: los defaults de `application.yml` ya apuntan a
`localhost:818x`. Para apuntar a otro lado:

```bash
MS_FACTURACION_URI=http://localhost:9002 ./mvnw spring-boot:run
```

Con los microservicios levantados:

```bash
curl http://localhost:8090/ms-operaciones/api/v1/placas
```

## Stack

- Java 21, Spring Boot 4.0.9, Spring Cloud 2025.1.4 (release train GA más reciente).
  Es un deployable independiente: no necesita igualar el Boot 4.1.x de los microservicios.

## Tests

```bash
./mvnw test
```

- `RutasGatewayTest`: levanta un backend HTTP de prueba y verifica el path con el que llega
  cada ruta (incluido el `StripPrefix` de analítica) y la propagación de `X-Trace-Id`.
- `GatewayApplicationTests`: comprueba que existan las cinco rutas y sus URIs por defecto.

## Pendiente

Solo aplica **si el gateway llega a desplegarse**; hoy no hace falta para uso local.

- Autenticación: hoy es pass-through sin validar token (decisión explícita). Sin auth, el
  gateway reenvía `X-Usuario-Id` / `X-Rol-Usuario-Id` / `X-Zona` tal cual, así que un cliente
  puede elegir su rol: aceptable en local, no en un entorno compartido.
- Pipeline CI (`azure-pipelines.yml`), Dockerfile y timeouts hacia los backends.
- Agregación de OpenAPI.
