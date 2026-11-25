# Resolucion de identidad operativa en servicios

Estado: diseno validado para refinamiento; sin implementar.

## Objetivo

Al registrar servicios en `ms-operaciones`, asociar el servicio al taller o certificador del usuario autenticado, segun el rol activo de su sesion. El cliente no debe poder elegir el identificador operativo que quedara persistido.

## Entendimiento

- El gateway identifica al usuario y propaga `X-User-Id` y `X-Rol-User-Id`.
- `X-Rol-User-Id` representa el rol activo de la sesion, no una lista de roles asignados.
- `ms-operaciones` consulta la API de `ms-identidad` usando el usuario y rol recibidos.
- Identidad valida que ese rol corresponda al usuario y devuelve solo la asociacion pertinente.
- Para un endpoint de Taller, Operaciones toma `id_taller` y lo asigna al servicio.
- Para endpoints que requieran contexto de Certificador, Operaciones toma `id_certificador`.
- Operaciones conserva sus validaciones de existencia, estado y permisos antes de persistir.

## Flujo propuesto

1. El cliente envia la solicitud funcional y su token; no envia `id_taller` ni `id_certificador` como dato autoritativo.
2. El gateway valida el token, determina la identidad y el rol activo, elimina headers de identidad que hubiera enviado el cliente y establece `X-User-Id` y `X-Rol-User-Id`.
3. Operaciones adapta esos headers a una consulta de servicio-a-servicio a Identidad.
4. Identidad valida la relacion usuario/rol y responde con el tipo de rol y solamente la asociacion operativa correspondiente.
5. Operaciones valida que el contexto corresponda al endpoint. El caso de uso asigna el ID operativo al registro en servidor y persiste.

## Responsabilidades

### Gateway

- Validar criptograficamente el token antes de confiar en sus claims.
- Propagar la identidad y el rol activo obtenidos de una fuente confiable.
- Sobrescribir o eliminar headers de identidad entrantes para evitar suplantacion.
- No seleccionar arbitrariamente el primer rol cuando el usuario tenga varios; debe propagar el rol activo.

### Identidad

- Ser la fuente de verdad de usuarios y asociaciones con talleres/certificadores.
- Exponer una consulta apta para servicio-a-servicio que valide usuario mas rol activo.
- Devolver solo el identificador operativo pertinente al rol validado.

### Operaciones

- Resolver el contexto operativo mediante un adaptador de salida hacia Identidad.
- Mantener la logica de aplicacion independiente de HTTP.
- Rechazar rol incompatible o asociacion ausente; no continuar sin contexto.
- Mantener la validacion existente del taller y asignar el ID resuelto en servidor.
- Aplicar el mismo patron para los casos de uso que requieran contexto de Certificador.

## Fallos y seguridad

- Usuario o rol no valido, rol no asignado, rol incompatible con el endpoint o asociacion requerida ausente: rechazar sin persistir. El status HTTP definitivo se debe alinear con el contrato comun de errores.
- Identidad no disponible o timeout: responder como dependencia no disponible (`503`) y no persistir.
- La llamada Operaciones -> Identidad debe autenticarse como servicio-a-servicio.
- El acceso directo a Operaciones debe estar restringido o autenticado de forma que no se puedan falsificar los headers del gateway.
- Configurar timeout acotado y propagar `X-Trace-Id`.

## Diferencias del codigo actual

- **Descartado (2026-11-25):** el gateway ya no resuelve identidad. Se eliminó `JwtSubHeaderGlobalFilter`; el cliente envía `X-User-Ciam-Id`, `X-User-Id`, `X-Rol-User-Id`, `X-Taller-Id`, `X-Certificador-Id` y los headers de alcance, y el gateway los reenvía sin tocarlos. La autenticación y la derivación de esos headers deben ocurrir antes del gateway (Ingress/WAF/BFF), que es la condición para que el diseño siga siendo seguro.
- En Operaciones existe el puerto `TallerActualProvider`, pero no una implementacion productiva; `RegistrarServiciosTallerService` esta condicionado a ese bean y por eso no se activa.

## Verificacion

- Gateway: token invalido rechazado; headers de identidad del cliente ignorados; rol activo propagado sin escoger el primer rol; errores de Identidad manejados sin fingir una identidad.
- Identidad: usuario/rol asignado devuelve solo la asociacion del rol; usuario/rol no asignado no revela ni devuelve asociaciones ajenas.
- Operaciones: Taller asigna el `id_taller` devuelto por Identidad; Certificador usa solo `id_certificador` en endpoints correspondientes; rol incompatible, asociacion ausente y error/timeout de Identidad no persisten cambios.
- Verificar que el caso de uso de registro se cree en el contexto Spring de produccion y que `mvn clean test` pase en cada repositorio modificado.

## Suposiciones y decisiones pendientes

- Se asume que la API de Identidad puede buscar al usuario por `X-User-Id` y validar el rol activo identificado por `X-Rol-User-Id`.
- Pendiente: ampliar el endpoint de usuario actual o agregar un endpoint interno dedicado para resolver el contexto operativo.
- Pendiente: definir autenticacion servicio-a-servicio y acceso de red entre microservicios.
- Pendiente: acordar los codigos HTTP exactos para usuario/rol/asociacion invalidos y rol incompatible.
- Pendiente: definir la fuente autoritativa del rol activo y ajustar el gateway para propagarla correctamente.

## Registro de decisiones

| Decision | Alternativas consideradas | Motivo |
|---|---|---|
| Operaciones resuelve la asociacion consultando la API de Identidad. | Gateway agrega `X-Taller-Id`/`X-Certificador-Id`; IDs operativos dentro del token. | Mantiene la propiedad de usuarios y asociaciones en Identidad y evita que el cliente elija el ID persistido. |
| Identidad valida el par usuario/rol y devuelve solo la asociacion de ese rol. | Devolver todas las asociaciones; confiar en un ID operativo recibido. | Reduce exposicion y evita mezclar identidades Taller/Certificador. |
| El caso de uso asigna la identidad operativa en servidor. | Aceptar `id_taller` o `id_certificador` en el body. | El cliente no debe controlar la autoria del registro. |
| Se preservan las validaciones del dominio en Operaciones. | Confiar unicamente en la respuesta de Identidad. | Identidad resuelve asociacion; Operaciones sigue siendo responsable de validar su dominio, por ejemplo taller activo. |
| La autenticacion de headers y de llamadas internas es requisito del diseno. | Confiar en headers sin verificar o permitir acceso directo sin proteccion. | Evita suplantacion de usuario/rol y abuso de la API interna. |
| (2026-11-25) El gateway reenvía headers de identidad tal cual y no consulta Identidad. | Mantener la resolución en el gateway (`JwtSubHeaderGlobalFilter`); firmar los headers en un BFF. | Se mueve la responsabilidad de autenticación fuera del gateway; el filtro duplicaba la lógica de Identidad en cada request. Riesgo aceptado: el gateway no debe quedar expuesto. |

## Alcance excluido

- Implementacion de cambios en Gateway, Identidad u Operaciones.
- Definicion final de rutas/DTOs, autenticacion servicio-a-servicio o codigos HTTP.
- Cache de contexto operativo; no se propone hasta medir necesidad y definir invalidacion.
