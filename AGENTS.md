# Gazel Ecosystem — Guía y Directrices para el Agente

Este repositorio centraliza las directrices, diseño transversal, historias de usuario (HU) y contexto operativo para el ecosistema de microservicios del **Portal de Revisiones Gazel (Terpel)**.

El código fuente de los microservicios se encuentra ubicado en:
`C:\Users\jaime.cabrera.ext\Documents\Gazel`

---

## 1. Mapa de Proyectos y Responsabilidades

El ecosistema Gazel está compuesto por 6 componentes backend principales, cada uno con un alcance y diseño especializado:

| Componente | Directorio en `Documents\Gazel` | Tipo / Runtime | Responsabilidad Principal | HUs Asociadas |
| :--- | :--- | :--- | :--- | :--- |
| **Facturación** | `at22pg-portal-revisiones-gazel-terpel-gazel-ms-facturacion` | Microservicio (Spring Boot 3 / Java 21) | Liquidaciones, pre-facturas, asignación y seguimiento de presupuestos anuales/mensuales. | HU 05, HU 05.1, HU 07 |
| **Soporte (Auditoría)** | `at22pg-portal-revisiones-gazel-terpel-gazel-cf-soporte` | Google Cloud Function Gen 2 (Java 21 nativo) | Ingesta asíncrona de eventos de auditoría y descargas vía Pub/Sub hacia `ms_soporte.auditoria_eventos` y `auditoria_descargas`. | Transversal (Auditoría de todas las HUs) |
| **Identidad** | `at22pg-portal-revisiones-gazel-terpel-gazel-ms-identidad` | Microservicio (Spring Boot 3 / Java 21) | Gestión de usuarios, catálogos (tipos de documento, certificadores), roles y módulos. Integración con CIAM / Keycloak. | HU 01, HU 01.1, HU 01.2, HU 01.3, HU 11 |
| **Analítica** | `at22pg-portal-revisiones-gazel-terpel-gazel-ms-analitica` | Microservicio (Spring Boot 3 / Java 21) | Tableros de control (dashboards), reportes gerenciales, indicadores de placas y KPIs temporales. | HU 06, HU 06.1, HU 06.2 |
| **Operaciones** | `at22pg-portal-revisiones-gazel-terpel-gazel-ms-operaciones` | Microservicio (Spring Boot 3 / Java 21) | Gestión de placas (HU02 / HU02.1 cargue individual y masivo), talleres, sucursales, procesamiento y validación de servicios de revisión GNV. | HU 02, HU 02.1, HU 03, HU 03.1, HU 03.2, HU 03.3, HU 04, HU 04.1, HU 04.2, HU 10, HU 10.1, HU 10.2 |
| **Parámetros** | `at22pg-portal-revisiones-gazel-terpel-gazel-ms-parametros` | Microservicio (Spring Boot 4 / Java 21) | Catálogos y gestión de tarifas de revisiones, certificadores y puesta a punto. | HU 10, HU 10.1, HU 10.2 |

---

## 2. Detalle de Proyectos y Dominios

### 2.1. Facturación (`ms-facturacion`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-ms-facturacion`
- **Paquete base:** `co.com.terpel.gazel.facturacion`
- **Controladores clave:**
  - `LiquidacionController`: Procesamiento y liquidación de servicios y revisiones aprobadas.
  - `PreFacturaController`: Generación de prefacturas previas al cobro/facturación final.
  - `PresupuestoController`: Control de techos presupuestales por programa y taller.
- **Esquema de BD:** `ms_facturacion` (Liquidaciones, detalle de facturación, presupuestos anuales).

### 2.2. Soporte y Auditoría (`cf-soporte`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-cf-soporte`
- **Tecnología:** Google Cloud Function Gen 2 pura (implementa `CloudEventsFunction` y `BackgroundFunction`), sin frameworks pesados (100% libre de Spring Boot, Hibernate o Lombok).
- **Mecanismo:** HikariCP + JDBC nativo con `PreparedStatement` para latencia mínima en cold start (<1s).
- **Eventos recibidos:** Pub/Sub topics de auditoría de operaciones, descargas masivas y cambios de estado.
- **Esquema de BD:** `ms_soporte` (`auditoria_eventos`, `auditoria_descargas`).
- **Resiliencia:** Tolerancia a formatos de fecha (ISO-8601, Epoch millis), autogeneración de UUID v4 si falta ID, reintentos administrados por Pub/Sub ante errores transitorios.

### 2.3. Identidad (`ms-identidad`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-ms-identidad`
- **Paquete base:** `co.com.terpel.gazel.identidad`
- **Controladores clave:**
  - `UsuarioController`: Altas, bajas lógicas, activación y asociación de talleres/certificadores.
  - `CatalogoController`: Tablas maestras.
  - `RolController` y `ModuloController`: Matriz de permisos y accesos por rol.
  - `CertificadorController`, `TipoIdentificacionController`.
- **Esquema de BD:** `ms_identidad`.
- **Catálogo canónico de Roles (HU10):**
  - `ADMINISTRADOR`
  - `TALLER`
  - `CERTIFICADOR`
  - `ANALISTA`
  - `TECNICO_COMERCIAL`
  - `DISTRIBUIDORA`

### 2.4. Analítica (`ms-analitica`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-ms-analitica`
- **Paquete base:** `co.com.terpel.gazel.analitica`
- **Controladores clave:**
  - `IndicadoresPlacasController`: Totalizadores y métricas de estados de placas (`ACTIVA`, `REGISTRADA`, `PAGADA`, `RECHAZADA`), filtros por programa y rangos temporales.
- **Esquema de BD:** `ms_analitica`.
- **Relación con otros servicios:** Consume métricas o réplicas analíticas. No realiza mutaciones de operaciones ni liquidaciones.

### 2.5. Operaciones (`ms-operaciones`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-ms-operaciones`
- **Paquete base:** `co.com.terpel.gazel.operacion`
- **Controladores clave:**
  - `PlacaController`: Gestión de vehículos y placas sujetas a revisión.
  - `TallerController` y `SucursalController`: Red de talleres autorizados y puntos de atención.
  - `DistribucionPlacasController`: HU06 — Distribución mensual y serie histórica de 12 meses de placas.
  - `DistribucionPresupuestoController`: HU06.1 — Presupuesto anual vs. liquidaciones acumuladas.
  - `ProcesarServicioController` y `ValidarServicioCertificadorController`: Flujo operativo de inspección técnica.
- **Esquema de BD:** `ms_operaciones`.

### 2.6. Parámetros (`ms-parametros`)
- **Ruta:** `C:\Users\jaime.cabrera.ext\Documents\Gazel\at22pg-portal-revisiones-gazel-terpel-gazel-ms-parametros`
- **Paquete base:** `co.com.terpel.gazel.parametros`
- **Controladores clave:** `CatalogoController`, `TarifaController`, `TarifaCertificadorController` y `TarifaPuestaPuntoController`.
- **Persistencia:** migraciones MySQL para catálogos y tarifas de revisiones, certificadores y puesta a punto.

---

## 3. Catálogo de Historias de Usuario (HU) en Markdown

Todas las historias de usuario han sido convertidas a formato Markdown en [`docs/historias-de-usuario/`](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/README.md):

### 🔐 Dominio Identidad y Accesos (`ms-identidad`)
- [HU 01 – Gestión de talleres](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-01-gestion-de-talleres.md)
- [HU 01.1 – Gestión de Certificadores](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-01.1-gestion-de-certificadores.md)
- [HU 01.2 – Gestión de usuarios](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-01.2-gestion-de-usuarios.md)
- [HU 01.3 – Gestión de roles y permisos](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-01.3-gestion-de-roles.md)
- [HU 11 – Mi perfil](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-11-mi-perfil.md)

### 🚗 Dominio Operativo y Placas (`ms-operaciones`)
- [HU 02 – Cargue individual de placas](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-02-cargue-individual-de-placas.md)
- [HU 02.1 – Cargue masivo de placas](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-02.1-cargue-masivo-de-placas.md)
- [HU 03 – Consulta, validación, edición y eliminación de placas usuario admin](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-03-consulta-validacion-edicion-eliminacion-placas-admin.md)
- [HU 03.1 – Validación, consulta y registro de servicios usuario Taller](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-03.1-validacion-consulta-registro-servicios-taller.md)
- [HU 03.2 – Validar, consultar y registrar servicios usuario certificador](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-03.2-validar-consultar-registrar-servicios-certificador.md)
- [HU 03.3 – Visualización consulta por placa, edición y eliminación](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-03.3-visualizacion-consulta-placa-edicion-eliminacion.md)
- [HU 04 – Aprobación de servicios Admin programas Vencidos y Altos consumidores](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-04-aprobacion-servicios-admin-vencidos-altos-consumidores.md)
- [HU 04.1 – Aprobación de servicios programa Puesta a punto](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-04.1-aprobacion-servicios-puesta-a-punto.md)
- [HU 04.2 – Aprobación de servicios usuario Taller programa Puesta a punto](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-04.2-aprobacion-servicios-taller-puesta-a-punto.md)
- [HU 10 – Gestión tarifas Revisiones](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-10-gestion-tarifas-revisiones.md)
- [HU 10.1 – Gestión tarifas Certificadores](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-10.1-gestion-tarifas-certificadores.md)
- [HU 10.2 – Gestión tarifas Puesta a punto](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-10.2-gestion-tarifas-puesta-a-punto.md)

### 💰 Dominio Facturación y Liquidaciones (`ms-facturacion`)
- [HU 05 – Reportes pre-facturas usuario administrador](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-05-reportes-prefacturas-administrador.md)
- [HU 05.1 – Reportes de prefacturas usuario Taller](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-05.1-reportes-prefacturas-taller.md)
- [HU 07 – Aprobar y rechazar liquidación como usuario taller](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-07-aprobar-rechazar-liquidacion-taller.md)

### 📊 Dominio Analítico y Tableros (`ms-analitica`)
- [HU 06 – Módulo Indicadores distribución placas](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-06-indicadores-distribucion-placas.md)
- [HU 06.1 – Módulo Indicadores Distribución de presupuesto](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-06.1-indicadores-distribucion-presupuesto.md)
- [HU 06.2 – Módulo Indicadores Certificadores y talleres](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/hu-06.2-indicadores-certificadores-talleres.md)

### 📑 Formatos y Diccionario de Campos
- [Diccionario de Formularios y Campos](file:///C:/Users/jaime.cabrera.ext/orca/projects/Gazel/docs/historias-de-usuario/Formularios-Campos-Historias-de-Usuario.md)

---

## 4. Estándares de Arquitectura y Código

Para cualquier cambio o adición en estos proyectos, el agente debe respetar estrictamente:

1. **Arquitectura Hexagonal (Ports & Adapters):**
   ```text
   src/main/java/co/com/terpel/gazel/<modulo>/
   ├── domain/
   │   ├── model/               <- Entidades y value objects inmutables
   │   └── exception/           <- BusinessException y validaciones de dominio
   ├── application/
   │   ├── port/in/             <- Interfaces UseCase ejecutadas por controladores
   │   ├── port/out/            <- Interfaces hacia persistencia y clientes externos
   │   └── service/             <- Implementación de casos de uso (orquestación)
   └── infrastructure/
       ├── adapter/in/web/      <- Controllers REST, DTOs request/response
       ├── adapter/out/persistence/ <- Entidades JPA/JDBC y repositorios
       ├── adapter/out/client/  <- Clientes HTTP hacia otros servicios
       └── config/              <- Beans de configuración y seguridad
   ```
2. **Inmutabilidad y Java 21:**
   - Usar `record` para DTOs, eventos y proyecciones de consulta.
   - Uso de `pattern matching`, streams y colecciones inmutables (`List.of()`, `Set.of()`).
3. **Manejo de Errores Unificado:**
   - Toda excepción de negocio debe heredar de `BusinessException`.
   - El adaptador web centraliza los errores con `GlobalExceptionHandler` retornando esquemas RFC 7807 (`ProblemDetail`) o la estructura estándar Terpel.
4. **Persistencia y Base de Datos:**
   - Motor: Neon PostgreSQL / Cloud SQL MySQL.
   - Separación estricta por esquemas de base de datos (`ms_operaciones`, `ms_analitica`, `ms_soporte`, `ms_identidad`, `ms_facturacion` y la persistencia propia de `ms-parametros`).
   - Las migraciones y scripts SQL deben ser idempotentes.
5. **Calidad y Verificación:**
   - Todo cambio debe compilar y pasar pruebas unitarias ejecutando `mvn clean test` (o `mvn clean verify`).
   - Mantener cobertura de pruebas sin regresiones (0 fallos).

---

## 5. Modo Ponytail

Este proyecto opera en **modo Ponytail (nivel full)** por defecto.

Principios clave que el agente debe seguir:
- **Cambio mínimo completo**: la solución más pequeña que resuelve todo el problema, sin features, opciones ni abstracciones que nadie pidió.
- **Reutiliza lo que ya existe**: helpers, patrones, librerías estándar o dependencias instaladas antes de escribir código nuevo.
- **Sin código "para después"**: nada de wrappers, conversiones, config, boilerplate ni abstracciones prematuras.
- **Inmutabilidad y Java 21**: `record` para DTOs/eventos, streams, colecciones inmutables.
- **Calidad obligatoria**: el cambio compila y pasa `mvn clean test` / `mvn clean verify` sin regresiones.
- **Comenta solo el *porqué* que el código no muestra**, en una línea.
- **Atajos con límite conocido**: marca con `shortcut: <límite>, <cuándo mejorar>`.

Nunca recortes: validación en fronteras de confianza, manejo de errores que evite pérdida de datos, seguridad, accesibilidad, ni lo que el usuario pidió explícitamente.
