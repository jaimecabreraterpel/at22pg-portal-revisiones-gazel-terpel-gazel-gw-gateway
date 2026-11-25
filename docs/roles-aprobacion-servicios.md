# Roles que aprueban servicios y revisiones

No hay un único rol: depende del programa y del momento del flujo. Este documento fija el contrato funcional (HU) y su estado en código.

## 1. Aprobación de servicios / revisiones (HU 04 y HU 04.1)

| Programa | Roles que aprueban/rechazan | Fuente |
|---|---|---|
| Vencidos y Altos consumidores | `ADMINISTRADOR` | HU 04: "usuarios con rol de Administrador aprobar/rechazar servicios…"; Escenario 7: "Soy un usuario con rol Administrador y cambie el estado del servicio" |
| Puesta a punto (gestión) | `ADMINISTRADOR` y `ANALISTA_TECNICO` | HU 04.1: "rol de Administrador y analista técnico aprobar/rechazar servicios de los programas Puesta a punto"; el analista solo ve la pestaña Puesta a punto |
| Puesta a punto (vista Taller) | `TALLER` — **solo consulta, detalle e historial**, sin botones Aprobar/Rechazar | HU 04.2: "rol de Taller visualizar el detalle de servicios" |

### Catálogo canónico de roles

`ms-identidad` → `TipoRol.java`:

```text
ADMINISTRADOR
TALLER
CERTIFICADOR
DISTRIBUIDOR
COMERCIAL
ANALISTA_TECNICO
```

El "analista técnico" de las HU equivale a `ANALISTA_TECNICO`.

> `AGENTS.md` del ecosistema lista `ANALISTA`, `TECNICO_COMERCIAL` y `DISTRIBUIDORA`; esos nombres **no** existen en el código. El catálogo de `ms-identidad` es la fuente de verdad.

## 2. Otros "aprobar" que no son aprobación de servicios

### CERTIFICADOR — validación técnica del servicio (HU 03.2)

```http
POST /ms-operaciones/api/operaciones/certificador/procesar
```

- Cambia `estadoRevision` a `APROBADO` o `RECHAZADO`.
- `ProcesarServicioService` exige que el servicio pertenezca al certificador en sesión (`X-Certificador-Id`); si no, `ServicioBusinessException.noAsociadaAlCertificador()`.

### TALLER — aprobación de liquidaciones/pagos (HU 07)

```http
POST /ms-operaciones/api/taller/liquidaciones/{id}/aprobar
POST /ms-operaciones/api/taller/liquidaciones/{id}/rechazar
```

- Aprueba u objeta el pago, no el servicio.
- El taller se resuelve desde `X-Taller-Id` (`RequestContextTallerActualProvider`).

## 3. Estado del código

| Flujo | Endpoint | Implementado |
|---|---|---|
| Aprobación de servicios por `ADMINISTRADOR` (Vencidos / Altos consumidores) | — | **No**. No existe endpoint que cambie el estado de un servicio para este rol. |
| Aprobación de servicios por `ADMINISTRADOR` / `ANALISTA_TECNICO` (Puesta a punto) | — | **No**. Solo hay consulta. |
| Consulta de servicios en gestión | `GET /api/gestion/servicios/consulta`<br>`GET /api/gestion/servicios/{id}` | Sí (`GestionServicioController`) |
| Validación técnica por `CERTIFICADOR` | `POST /api/operaciones/certificador/procesar` | Sí (`ProcesarServicioController`) |
| Aprobación de liquidaciones por `TALLER` | `POST /api/taller/liquidaciones/{id}/aprobar`<br>`POST /api/taller/liquidaciones/{id}/rechazar` | Sí (`TallerLiquidacionController`) |
| Consulta de servicios vista `TALLER` (HU 04.2) | `GET /api/taller/...` | Consulta/detalle, sin aprobación |

### Brecha de alcance por rol

`GatewayRolScopeResolver` (`ms-operaciones`) resuelve el alcance por UUID configurado en `operaciones.servicios.gestion`:

```yaml
operaciones:
  servicios:
    gestion:
      rol-administrador: ...
      rol-asistente: ...
      rol-ventas: ...
```

No distingue `ANALISTA_TECNICO`. Para HU 04.1 falta el mapeo de ese rol y la restricción a la pestaña Puesta a punto (hoy `ADMINISTRADOR` obtiene alcance global y el analista no es reconocido → `403 "Rol o alcance no reconocido para consulta de gestion"`).

## 4. Resumen

- `ADMINISTRADOR`: aprueba Vencidos, Altos consumidores y Puesta a punto.
- `ANALISTA_TECNICO`: aprueba solo Puesta a punto.
- `CERTIFICADOR`: valida el servicio técnico (aprueba/rechaza la revisión cargada).
- `TALLER`: solo consulta servicios; aprueba liquidaciones.