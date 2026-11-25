# Inventario: base de datos `gazel` vs entidades de los repositorios

**Fecha de levantamiento:** 2026-11-05  
**Base inspeccionada:** MySQL 8.4.13, esquema `gazel`  
**Método:** consultas de solo lectura a `information_schema` y revisión de entidades JPA/JDBC bajo `C:\Users\jaime.cabrera.ext\Documents\Gazel`.

## Resumen físico actual

| Métrica | Resultado |
|---|---:|
| Tablas base | 46 |
| Columnas | 357 |
| Relaciones FK | 67 |
| Registros estimados en tablas | 0 |
| Tablas de historial Flyway | 0 |
| Repositorios revisados | 6 |
| Declaraciones `@Entity` | 52 |
| Tablas lógicas JPA distintas | 42 |

La base real tiene tablas con prefijos de dominio: `identidad_`, `operaciones_`, `facturacion_`, `parametrizacion_`, `analitica_` y `soporte_`. Ninguna entidad JPA usa esos prefijos en `@Table` ni especifica `schema`; sin configuración adicional, no apuntan a las tablas físicas existentes.

## Estado general

| Repositorio | Entidades/acceso | Tablas físicas propias | Estado de compatibilidad |
|---|---:|---:|---|
| `ms-identidad` | 8 entidades | 5 `identidad_*` | Crítico: tablas sin prefijo, IDs numéricos y columnas divergentes. |
| `ms-operaciones` | 28 entidades, 23 nombres lógicos | 26 `operaciones_*` | Alto: prefijos ausentes, entidades duplicadas y SQL PostgreSQL/nombres antiguos. |
| `ms-facturacion` | 3 entidades | 4 `facturacion_*` | Crítico: IDs numéricos, tablas/columnas de presupuesto ausentes o distintas. |
| `ms-parametros` | 9 entidades | 7 `parametrizacion_*` | Crítico: cuatro IDs numéricos, catálogo regional incompleto y referencias cruzadas. |
| `ms-analitica` | 2 entidades | 2 `analitica_*` | Medio: UUID y columnas alineados en esencia, pero tabla física distinta. |
| `cf-soporte` | JDBC para 1 tabla | 2 `soporte_*` | Alto: tabla/esquema antiguo y `tipo_proceso` obligatorio no se inserta. |

## Inventario de tablas físicas

| Dominio | Tablas actuales |
|---|---|
| Identidad (5) | `identidad_roles`, `identidad_modulos`, `identidad_permisos_roles`, `identidad_usuarios`, `identidad_perfil` |
| Operaciones (26) | `operaciones_catalogo_departamentos`, `operaciones_catalogo_ciudades`, `operaciones_catalogo_tipos_vehiculo`, `operaciones_catalogo_marcas_vehiculo`, `operaciones_catalogo_lineas_servicio`, `operaciones_catalogo_origenes_falla`, `operaciones_catalogo_opciones_falla`, `operaciones_catalogo_tipos_revision`, `operaciones_catalogo_programas`, `operaciones_motivo_errores_cargue`, `operaciones_talleres`, `operaciones_vehiculos`, `operaciones_sucursales`, `operaciones_certificadores`, `operaciones_registro_placas_programas`, `operaciones_historial_cargue_masivo`, `operaciones_errores_cargue_masivo`, `operaciones_historial_revisiones_placas`, `operaciones_servicios_puesta_punto`, `operaciones_archivos_servicios_pap`, `operaciones_detalle_fallas_puesta_punto`, `operaciones_servicios_revisiones`, `operaciones_aprobaciones_certificador`, `operaciones_historial_estados_servicios`, `operaciones_vehiculo_archivos`, `operaciones_log_revisiones_vehiculo` |
| Facturación (4) | `facturacion_pre_facturas`, `facturacion_liquidaciones`, `facturacion_respuestas_pre_facturas_taller`, `facturacion_presupuestos_programas` |
| Parametrización (7) | `parametrizacion_catalogo_tipos_identificacion`, `parametrizacion_catalogo_regionales`, `parametrizacion_catalogo_promociones`, `parametrizacion_tarifas`, `parametrizacion_historial_cargue_tarifas`, `parametrizacion_tarifas_certificadores`, `parametrizacion_tarifas_puesta_punto` |
| Analítica (2) | `analitica_registro_reportes`, `analitica_indicadores_cache` |
| Soporte (2) | `soporte_auditoria_eventos`, `soporte_archivos_adjuntos` |

## Comparación por repositorio

### `ms-identidad`

| Entidad y evidencia | Tabla física equivalente | Diferencia confirmada contra la base actual |
|---|---|---|
| `UsuarioEntity` — `...ms-identidad/src/main/java/co/com/terpel/gazel/identidad/infrastructure/adapter/out/persistence/entity/UsuarioEntity.java:18` | `identidad_usuarios` | La entidad usa `usuarios`, `Integer` e `IDENTITY`; la base usa `CHAR(36)`. La entidad tiene `telefono`, `password_hash`, `token_recuperacion`, `expiracion_token`, `ruta_foto_perfil`, `fecha_ultimo_cambio_password`; la base tiene `numero_telefono` y `estado_eliminacion`, pero no los seis primeros. |
| `RolEntity.java:17` | `identidad_roles` | `roles` vs `identidad_roles`; `Integer`/IDENTITY vs `CHAR(36)`. |
| `ModuloEntity.java:14` | `identidad_modulos` | `modulos` vs `identidad_modulos`; `Integer`/IDENTITY vs `CHAR(36)`. |
| `PermisoRolEntity.java:14`, `PermisoRolId.java:15` | `identidad_permisos_roles` | Nombre sin prefijo; PK compuesta `Integer,Integer` vs `CHAR(36),CHAR(36)`. |
| `TipoIdentificacionEntity.java:17` | `parametrizacion_catalogo_tipos_identificacion` | La entidad de identidad apunta a un catálogo cuyo dueño físico es parámetros; ID `Integer` vs `CHAR(36)`. |
| `CertificadorEntity.java:17` | `operaciones_certificadores` | La entidad de identidad apunta a una tabla cuyo dueño físico es operaciones; PK/FK numéricas vs UUID textual. |
| `AuditoriaEventoEntity.java:15` | `soporte_auditoria_eventos` | `Long`/IDENTITY vs `CHAR(36)`; faltan en entidad `tipo_proceso`, `id_usuario_accion`, `id_rol_usuario`, `tipo_descarga`, `filtros_aplicados`, `detalle`, `zona`, `fecha_hora`; sobran `usuario`, `ip_origen`, `user_agent`, `fecha_evento`. |
| `ArchivoAdjuntoEntity.java:18` | `soporte_archivos_adjuntos` | Nombre sin prefijo y `Integer` para `id_archivo`, `id_entidad`, `id_usuario`, mientras la base usa `CHAR(36)`. |

**Tabla física sin entidad de identidad:** `identidad_perfil`. Debe decidirse si HU 11 persiste el perfil separado o si la tabla queda fuera del modelo activo.

### `ms-operaciones`

Las entidades usan UUID `String` en su mayoría, pero todas omiten el prefijo `operaciones_`. El repositorio tiene dos familias simultáneas: `persistence/entity` y `persistence/jpa/entity`.

| Hallazgo | Evidencia | Impacto frente a la base actual |
|---|---|---|
| Entidades duplicadas para la misma tabla | `CatalogoProgramaEntity.java:13` / `jpa/entity/ProgramaEntity.java:13`; `CatalogoTipoVehiculoEntity.java:12` / `jpa/entity/TipoVehiculoEntity.java:9`; `CatalogoTipoRevisionEntity.java:12` / `jpa/entity/TipoRevisionEntity.java:9`; ambas `CertificadorEntity`; ambas `ServicioRevisionEntity` | Dos modelos para una tabla física pueden divergir en columnas, nulabilidad y operaciones de escritura. |
| Catálogo de identificación no es de operaciones | `TipoIdentificacionEntity.java:15` | Mapea `catalogo_tipos_identificacion`, pero la tabla actual es `parametrizacion_catalogo_tipos_identificacion`. |
| `catalogo_departamentos` parcial | `DepartamentoEntity.java:15` | La base tiene `codigo_departamento` e `id_regional` que no están en esta entidad. |
| `catalogo_ciudades` parcial | `CiudadEntity.java:15` | La base tiene `codigo_dane` que no está en esta entidad. |
| Taller parcial | `TallerEntity.java:21` | La base tiene `ruta_archivo_rut`, no mapeada por la entidad. |
| Cargue masivo parcial | `jpa/entity/HistorialCargueMasivoEntity.java:11` | La base tiene `estado_cargue`, no mapeado. |
| Placas-programa parcial | `jpa/entity/RegistroPlacaProgramaEntity.java:20` | La base tiene `id_vehiculo`, `fecha_fin_vigencia`, `nombre_propietario`, `numero_documento_propietario`, no mapeados por esta entidad de flujo. |
| Puesta a punto parcial | `jpa/entity/ServicioPuestaPuntoEntity.java:16` | La base tiene `ruta_archivo_adjunto`, `id_pre_factura`, `id_vehiculo`, `numero_orden`, fechas de servicio, `valor_total_servicio`, `id_certificador`; no están en esta entidad. |
| Revisión con modelo divergente | `entity/ServicioRevisionEntity.java:14`; `jpa/entity/ServicioRevisionEntity.java:17` | La entidad de taller usa `id_programa`, pero esa columna no existe en `operaciones_servicios_revisiones`. Ambas entidades requieren contraste de todas sus columnas antes de activar validación JPA. |
| SQL PostgreSQL y nombres antiguos | `DetallePlacaJpaAdapter.java:31`; `PostgresTallerRepository.java:38`; `PresupuestoJpaAdapter.java:20`; `ReporteDescargaJdbcAdapter.java:20` | Usa `DISTINCT ON`, `public.talleres`, tablas sin prefijo, `liquidaciones_talleres`, `historial_presupuestos_programas` y `auditoria_descargas`. Esas referencias no existen con esos nombres en la base actual. |

**Tablas físicas de operaciones sin entidad propia:** `operaciones_motivo_errores_cargue`, `operaciones_historial_estados_servicios`, `operaciones_vehiculo_archivos` y `operaciones_log_revisiones_vehiculo`. No implica crear cuatro CRUD: pueden ser catálogo, historial, adjuntos o auditoría con adaptadores específicos.

### `ms-facturacion`

| Entidad y evidencia | Tabla física equivalente | Diferencia confirmada contra la base actual |
|---|---|---|
| `PreFacturaEntity.java:14` | `facturacion_pre_facturas` | `pre_facturas` y PK/usuario `Integer` con IDENTITY vs tabla/IDs `CHAR(36)`. |
| `LiquidacionEntity.java:14` | `facturacion_liquidaciones` | `liquidaciones` y IDs `Integer` vs UUID textual. La entidad requiere `id_programa`, que no existe en la tabla actual. |
| `PresupuestoProgramaEntity.java:14` | `facturacion_presupuestos_programas` | IDs numéricos vs UUID textual. La entidad requiere `version`, que no existe en la tabla actual. |

**Tabla física sin entidad:** `facturacion_respuestas_pre_facturas_taller`; es relevante para HU 07 si se habilita la respuesta del taller.

**Tablas que el código de operaciones espera pero no existen en la base actual:** `liquidaciones_talleres` e `historial_presupuestos_programas`, consultadas/escritas por `PresupuestoJpaAdapter.java:20-148`. Deben añadirse por migración aprobada o sustituirse en la consulta; no crear tablas ad hoc.

### `ms-parametros`

| Entidad y evidencia | Tabla física equivalente | Diferencia confirmada contra la base actual |
|---|---|---|
| `TarifaEntity.java:23` | `parametrizacion_tarifas` | `tarifas`; `Integer` para tarifa/regional/ciudad vs `CHAR(36)`; el comentario declara UUID pero el código no. |
| `TarifaCertificadorEntity.java:19` | `parametrizacion_tarifas_certificadores` | `Integer` para tarifa, regional, ciudad y certificador vs UUID textual. |
| `TarifaPuestaPuntoEntity.java:19` | `parametrizacion_tarifas_puesta_punto` | `Integer` para tarifa y fallas vs UUID textual. |
| `CatalogoRegionalEntity.java:20` | `parametrizacion_catalogo_regionales` | `Integer` vs UUID; la entidad no mapea la columna física obligatoria `codigo_regional`. |
| `CatalogoDepartamentoEntity.java:18` | `operaciones_catalogo_departamentos` | Tabla de operaciones, no de parámetros; la entidad sí usa UUID y columnas principales. |
| `CatalogoCiudadEntity.java:18` | `operaciones_catalogo_ciudades` | Tabla de operaciones, no de parámetros; la entidad sí usa UUID y columnas principales. |
| `CatalogoOrigenFallaEntity.java:18` | `operaciones_catalogo_origenes_falla` | Tabla de operaciones, no de parámetros; la entidad incluye `estado`, columna que no existe en la base actual. |
| `CatalogoOpcionFallaEntity.java:15` | `operaciones_catalogo_opciones_falla` | Tabla de operaciones, no de parámetros; la entidad incluye `estado`, columna que no existe en la base actual. |
| `CertificadorEntity.java:20` | `operaciones_certificadores` | Tabla de operaciones, no de parámetros; UUID y mayoría de columnas coinciden. |

**Tablas físicas sin entidad de parámetros:** `parametrizacion_catalogo_tipos_identificacion`, `parametrizacion_catalogo_promociones`, `parametrizacion_historial_cargue_tarifas`.

### `ms-analitica`

| Entidad y evidencia | Tabla física equivalente | Diferencia confirmada contra la base actual |
|---|---|---|
| `RegistroReporteEntity.java:14` | `analitica_registro_reportes` | UUID y columnas principales compatibles; falta el prefijo físico. |
| `IndicadoresCacheEntity.java:12` | `analitica_indicadores_cache` | UUID y columnas principales compatibles; falta el prefijo físico. |

No hay tabla física de analítica sin entidad. Los clientes hacia operaciones deben conservarse; analítica no requiere mapear las tablas operativas para consultar indicadores.

### `cf-soporte`

| Acceso y evidencia | Tabla física equivalente | Diferencia confirmada contra la base actual |
|---|---|---|
| `AuditoriaRepository.java:42,65-68,146-163` | `soporte_auditoria_eventos` | Construye `ms_soporte.auditoria_eventos`, pero la tabla actual se llama `soporte_auditoria_eventos` dentro de `gazel`. El `INSERT` no envía `tipo_proceso`, que es `NOT NULL`; tampoco incluye `tipo_descarga`, `filtros_aplicados` ni `detalle`. |

`AuditoriaRepository` sí genera UUID v4 cuando falta `id_auditoria` (`AuditoriaRepository.java:134-141`). `soporte_archivos_adjuntos` no tiene acceso JDBC en este repositorio; identidad lo mapea con tipos numéricos heredados.

## Relaciones de base relevantes para el inventario

- Parámetros posee tipos de identificación y regionales; operaciones depende de ambos por FK.
- Operaciones posee ciudades, departamentos, talleres, vehículos, certificadores, fallas y servicios; parámetros, facturación y analítica los referencian por FK.
- Identidad posee roles y usuarios; facturación, analítica, parámetros y soporte los referencian por FK.
- Facturación posee prefacturas; servicios de puesta a punto las referencia por FK.
- La base no tiene FK para algunos campos semánticamente relacionados, entre ellos `identidad_usuarios.id_taller`, `identidad_usuarios.id_certificador`, `identidad_usuarios.id_tipo_identificacion` y `operaciones_aprobaciones_certificador.id_usuario_certificador`. La decisión de agregarlas requiere validar el flujo y los datos.

## Prioridad de remediación

1. Configurar nombres físicos con prefijo y datasource `gazel` para cada backend, manteniendo `ddl-auto=validate` en perfiles MySQL.
2. Cambiar de punta a punta IDs `Integer`/`Long`/IDENTITY a UUID `String` donde la base usa `CHAR(36)`; empezar por identidad, parámetros y facturación.
3. Corregir las columnas ausentes o sobrantes de identidad, parámetros y operaciones antes de activar validación Hibernate.
4. Reescribir SQL nativo de operaciones y soporte para MySQL 8 y tablas físicas actuales.
5. Resolver por decisión de producto los objetos ausentes que hoy requieren los indicadores de presupuesto: `liquidaciones_talleres`, `historial_presupuestos_programas`, `id_programa` de liquidaciones y `version` de presupuestos.
6. Definir adaptadores mínimos para las tablas sin entidad según el flujo, en lugar de crear entidades sin uso.

## Limitaciones del levantamiento

- La base está vacía, por lo que el inventario cubre estructura, no calidad ni migración de datos.
- No se ejecutaron operaciones DDL/DML, pruebas Maven, Flyway ni arranque de microservicios.
- Las diferencias de columna se basan en anotaciones de persistencia y consultas ubicadas; modelos de dominio o DTO no equivalen necesariamente a columnas.
