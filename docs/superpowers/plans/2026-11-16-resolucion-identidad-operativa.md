# Resolucion de identidad operativa Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Asociar servicios al taller o certificador del usuario autenticado y rol activo, sin aceptar IDs operativos autoritativos desde el cliente.

**Architecture:** Gateway valida sesion y propaga `X-User-Id` y `X-Rol-User-Id` confiables. Operaciones consulta Identidad para validar usuario+rol y resolver solo la asociacion pertinente. El caso de uso conserva validaciones de dominio y persiste el ID resuelto en servidor.

**Tech Stack:** Java 21; Spring Boot 4 / Spring Cloud Gateway; Spring Boot 3; WebFlux; cliente HTTP existente o WebClient; JUnit 5, Mockito, Spring Boot Test y Maven.

---

## Alcance y orden

Cambio transversal secuencial: implementar Identidad, consumirlo en Operaciones y verificar Gateway. Paths relativos a cada repositorio. Revisar y preservar cambios locales antes de editar.

- Gateway: `C:/Users/jaime.cabrera.ext/Documents/Gazel/at22pg-portal-revisiones-gazel-terpel-gazel-gw-gateway`
- Identidad: `C:/Users/jaime.cabrera.ext/Documents/Gazel/at22pg-portal-revisiones-gazel-terpel-gazel-ms-identidad`
- Operaciones: `C:/Users/jaime.cabrera.ext/Documents/Gazel/at22pg-portal-revisiones-gazel-terpel-gazel-ms-operaciones`

## File Map

### Identidad
- Modify `src/main/java/co/com/terpel/gazel/identidad/infrastructure/adapter/in/web/UsuarioController.java` for internal user+role context lookup.
- Add `src/main/java/co/com/terpel/gazel/identidad/infrastructure/adapter/in/web/dto/response/ContextoOperativoUsuarioDTO.java` with user, validated role, and only the applicable association.
- Modify `GestionarUsuarioUseCase.java`, `UsuarioService.java`, `UsuarioServiceTest.java`, and `UsuarioControllerTest.java` for lookup and coverage.

### Operaciones
- Add `src/main/java/co/com/terpel/gazel/operacion/application/port/out/ContextoOperativoUsuarioPort.java`.
- Add `src/main/java/co/com/terpel/gazel/operacion/infrastructure/adapter/out/client/IdentidadContextoOperativoAdapter.java` with timeout, service auth, trace propagation, and error mapping.
- Configure service URI/timeout/auth in existing config and `src/main/resources/application.yml`; do not commit secrets.
- Add `src/main/java/co/com/terpel/gazel/operacion/infrastructure/security/ContextoOperativoActualAdapter.java` implementing `TallerActualProvider` from `X-User-Id` and `X-Rol-User-Id`.
- Modify `RegistrarServiciosTallerService.java` only as needed to preserve separate role context, server-side ID assignment, and active workshop validation.
- Add adapter/context tests and update `RegistrarServiciosTallerServiceTest.java`.

### Gateway
- Modify `src/main/java/co/com/terpel/gazel/gateway/infrastructure/config/JwtSubHeaderGlobalFilter.java` to validate JWT, establish active role, strip client identity headers on every path, and never forward unverified identity.
- Modify `src/main/resources/application.yml` and deployment config for issuer/JWKS and bounded Identity-call timeout; no committed secrets.
- Modify `src/test/java/co/com/terpel/gazel/gateway/RutasGatewayTest.java` for signed/invalid tokens, forged headers, absent role, and trusted propagation.
- Update README/config docs if they claim Gateway does not authenticate.

## Implementation Tasks

### 1. Identity context contract
- [x] **1.1 Tests first:** `UsuarioServiceTest`: `TALLER` returns `idTaller` only; `CERTIFICADOR` returns `idCertificador` only; reject unassigned role, inactive/missing user, or missing association without revealing the other association.
- [x] **1.2 Implement application operation** using existing `Usuario` fields. Validate requested role against assigned role before resolving; follow existing `BusinessException` status conventions.
- [x] **1.3 Add response record and route:** `GET /api/v1/usuarios/{idUsuario}/contexto-operativo/{idRol}` returns user, validated role and only the applicable ID. Protect with required service-to-service key of at least 32 characters; missing configuration prevents startup.
- [x] **1.4 Controller tests:** both role responses, malformed IDs, unauthenticated request, and association failures covered at service/controller boundaries.
- [x] **1.5 Run `./mvnw test`** at Identity root; full `./mvnw test` passed (293 tests).

### 2. Operaciones context resolution
- [ ] **2.1 Tests first:** `X-User-Id` and `X-Rol-User-Id=TALLER` resolve through Identity; missing headers, incompatible role, missing association, timeout, and non-2xx fail closed.
- [ ] **2.2 Add outbound port and HTTP adapter** using Task 1 contract. Send trace ID and service credential, use bounded configurable timeout, map Identity timeout/unavailability to standard 503. Never log credentials or bearer tokens.
- [ ] **2.3 Add production `TallerActualProvider` adapter** reading both headers and requiring Taller role. Resolve `idTaller` from Identity, never body or caller-supplied `X-Taller-Id`. Verify Spring creates `RegistrarServiciosTallerService` in production context.
- [ ] **2.4 Preserve domain behavior:** keep existing workshop existence/active check and assignment to `ServicioRevision` / `ServicioPuestaPunto` before persistence. Invalid role/association or Identity failure must not invoke persistence.
- [ ] **2.5 Certificador:** use the same contract only in existing use cases that require Certificador context; do not add new workflows.
- [ ] **2.6 Run Operations tests:** `./mvnw -Dtest=RegistrarServiciosTallerServiceTest,ContextoOperativoActualAdapterTest test`, then `./mvnw clean test` at Operations root; expect exit 0 including Spring context tests.

### 3. Gateway trusted identity headers
- [ ] **3.1 Tests first:** prove client identity headers never reach backend if Authorization is absent, token malformed, Identity unavailable, or Identity omits identity fields.
- [ ] **3.2 Establish active-role source:** use role selected in authenticated session and validate against Identity for the resolved user. Never infer it from first `roles` element. If current token/session contract lacks the selection, fail closed and document upstream contract needed.
- [ ] **3.3 Replace payload-only JWT parsing** with cryptographic validation using configured issuer/JWKS and supported algorithms. Invalid, expired, wrong-issuer/audience tokens cannot produce trusted headers. Externalize config and bound timeouts.
- [ ] **3.4 Update header mutation** to remove incoming identity headers unconditionally and set only verified values. If user/role cannot be established, do not forward forged values; use agreed auth error for protected routes.
- [ ] **3.5 Update Gateway tests** with signed test token or verifier fixture; verify valid user/role, invalid credentials, forged headers stripped/overwritten, and retain route/trace tests.
- [ ] **3.6 Run Gateway tests:** `./mvnw clean test` at Gateway root; expect exit 0.

### 4. End-to-end verification
- [ ] **4.1 Integration test:** Gateway -> Identity -> Operations using existing integration infrastructure or controlled servers. Assert client body does not provide authoritative workshop ID, resolved `idTaller` is persisted, and trace ID reaches Identity.
- [ ] **4.2 Negative cases:** forged headers, Taller without association, Certificador on Taller-only route, Identity timeout/unavailability, invalid active role. Assert no service is persisted.
- [ ] **4.3 Run in dependency order:** Identity `./mvnw clean test`, Operations `./mvnw clean test`, Gateway `./mvnw clean test`; record any suite blocked by external services/credentials.
- [ ] **4.4 Update `docs/diseno-contexto-identidad-operativa.md`** with final route, DTO, status mapping, internal auth, active role source, and environment variables.

## Acceptance Criteria
- Gateway never trusts caller-supplied identity headers or derives identity from unverified JWT.
- Active role is validated for the authenticated user; no component substitutes the first role in a list.
- Operations resolves the operational ID from Identity, not the request.
- Taller registration persists resolved `idTaller` and retains active-workshop validation; Certificador context is not mixed into Taller registration.
- Identity failures/timeouts fail closed and cannot persist ownership absent or chosen by client.
- All three repositories pass tests, or infrastructure-dependent gaps are explicitly recorded.

## Execution Notes
- Identity and Operations are outside the writable workspace granted for this session. Obtain write access there before implementation; do not add their sources to Gateway.
- Gateway currently has no Spring Security resource-server dependency, and its filter decodes JWT without verifying the signature. Cryptographic validation and active-role source are mandatory prerequisites, not existing behavior.
- Preserve all pre-existing user changes. Run current tests before editing when practical, then focused tests after each task and clean test suites at the end.
- Out of scope: caching, new role-selection UI, client-supplied operational IDs, unrelated authorization refactors.
