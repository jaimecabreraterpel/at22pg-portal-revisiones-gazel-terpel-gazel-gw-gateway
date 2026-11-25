# Catálogo de Historias de Usuario (HU) — Portal Revisiones Gazel

Este directorio contiene las especificaciones funcionales y de negocio en formato Markdown (.md) extraídas de las Historias de Usuario oficiales del proyecto Gazel (Terpel).

---

## 1. Mapeo de HUs por Microservicio y Dominio

### 🔐 ms-identidad (Identidad, Usuarios y Permisos)
| Código | Archivo | Descripción Principal |
| :--- | :--- | :--- |
| **HU 01** | [`hu-01-gestion-de-talleres.md`](./hu-01-gestion-de-talleres.md) | Altas, bajas lógicas y parametrización de talleres autorizados. |
| **HU 01.1** | [`hu-01.1-gestion-de-certificadores.md`](./hu-01.1-gestion-de-certificadores.md) | Creación y administración de entidades certificadoras. |
| **HU 01.2** | [`hu-01.2-gestion-de-usuarios.md`](./hu-01.2-gestion-de-usuarios.md) | Gestión de usuarios, roles asignados y activación/inactivación. |
| **HU 01.3** | [`hu-01.3-gestion-de-roles.md`](./hu-01.3-gestion-de-roles.md) | Matriz de roles y permisos por módulo (HU10 canónico). |
| **HU 11** | [`hu-11-mi-perfil.md`](./hu-11-mi-perfil.md) | Consulta y actualización de información personal del usuario autenticado. |

### 🚗 ms-operaciones (Vehículos, Placas, Servicios y Tarifas)
| Código | Archivo | Descripción Principal |
| :--- | :--- | :--- |
| **HU 02** | [`hu-02-cargue-individual-de-placas.md`](./hu-02-cargue-individual-de-placas.md) | Registro y validación individual de vehículos con GNV. |
| **HU 02.1** | [`hu-02.1-cargue-masivo-de-placas.md`](./hu-02.1-cargue-masivo-de-placas.md) | Ingesta y validación por archivo masivo (Excel/CSV). |
| **HU 03** | [`hu-03-consulta-validacion-edicion-eliminacion-placas-admin.md`](./hu-03-consulta-validacion-edicion-eliminacion-placas-admin.md) | Consola de administración de placas para el Administrador. |
| **HU 03.1** | [`hu-03.1-validacion-consulta-registro-servicios-taller.md`](./hu-03.1-validacion-consulta-registro-servicios-taller.md) | Flujo del rol Taller para registrar servicios e inspecciones. |
| **HU 03.2** | [`hu-03.2-validar-consultar-registrar-servicios-certificador.md`](./hu-03.2-validar-consultar-registrar-servicios-certificador.md) | Flujo del rol Certificador para validación y emisión de certificados. |
| **HU 03.3** | [`hu-03.3-visualizacion-consulta-placa-edicion-eliminacion.md`](./hu-03.3-visualizacion-consulta-placa-edicion-eliminacion.md) | Edición, consulta detallada y eliminación de registros de vehículos. |
| **HU 04** | [`hu-04-aprobacion-servicios-admin-vencidos-altos-consumidores.md`](./hu-04-aprobacion-servicios-admin-vencidos-altos-consumidores.md) | Aprobación técnica de servicios para programas Vencidos y Altos Consumidores. |
| **HU 04.1** | [`hu-04.1-aprobacion-servicios-puesta-a-punto.md`](./hu-04.1-aprobacion-servicios-puesta-a-punto.md) | Aprobación de servicios del programa Puesta a punto por Administrador. |
| **HU 04.2** | [`hu-04.2-aprobacion-servicios-taller-puesta-a-punto.md`](./hu-04.2-aprobacion-servicios-taller-puesta-a-punto.md) | Gestión del Taller para el programa Puesta a punto. |
| **HU 10** | [`hu-10-gestion-tarifas-revisiones.md`](./hu-10-gestion-tarifas-revisiones.md) | Configuración de tarifas base para revisiones periódicas GNV. |
| **HU 10.1** | [`hu-10.1-gestion-tarifas-certificadores.md`](./hu-10.1-gestion-tarifas-certificadores.md) | Esquema tarifario por certificadora autorizada. |
| **HU 10.2** | [`hu-10.2-gestion-tarifas-puesta-a-punto.md`](./hu-10.2-gestion-tarifas-puesta-a-punto.md) | Esquema tarifario específico para el programa Puesta a punto. |

### 💰 ms-facturacion (Liquidaciones y Pre-facturación)
| Código | Archivo | Descripción Principal |
| :--- | :--- | :--- |
| **HU 05** | [`hu-05-reportes-prefacturas-administrador.md`](./hu-05-reportes-prefacturas-administrador.md) | Consola gerencial para generación y reporte de pre-facturas. |
| **HU 05.1** | [`hu-05.1-reportes-prefacturas-taller.md`](./hu-05.1-reportes-prefacturas-taller.md) | Consulta de pre-facturas y servicios facturables para el Taller. |
| **HU 07** | [`hu-07-aprobar-rechazar-liquidacion-taller.md`](./hu-07-aprobar-rechazar-liquidacion-taller.md) | Flujo de aprobación o rechazo de liquidaciones periódicas por el Taller. |

### 📊 ms-analitica (Dashboards e Indicadores)
| Código | Archivo | Descripción Principal |
| :--- | :--- | :--- |
| **HU 06** | [`hu-06-indicadores-distribucion-placas.md`](./hu-06-indicadores-distribucion-placas.md) | Indicadores de distribución mensual y serie temporal de 12 meses. |
| **HU 06.1** | [`hu-06.1-indicadores-distribucion-presupuesto.md`](./hu-06.1-indicadores-distribucion-presupuesto.md) | Control y seguimiento del presupuesto anual vs. liquidaciones acumuladas. |
| **HU 06.2** | [`hu-06.2-indicadores-certificadores-talleres.md`](./hu-06.2-indicadores-certificadores-talleres.md) | Métricas operativas por certificador y taller. |

### 📑 Diccionario de Formularios y Campos
- [`Formularios-Campos-Historias-de-Usuario.md`](./Formularios-Campos-Historias-de-Usuario.md): Catálogo de campos, obligatoriedad, tipos de datos, validaciones y pesos máximos de adjuntos.
