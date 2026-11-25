# Formularios y Campos — Historias de Usuario Gazel


## Hoja1

|  | HU | Campo | Obligatoriedad Si o No | Tipo campo | Editable Si o No | Cantidad caracteres | Peso de archivo | obsevaciones |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | HU 01 – Gestión de talleres | Tipo de identificación | si | Lista desplegable | No |  |  | La unica opcion sera (NIT) |
|  |  | Numero de documento | si | Numerico | No | maximo 50 caracteres |  | Control en front con el maximo |
|  |  | Nombre comercial | si | Alfa numerico | No | maximo 150 carcateres |  | Control en front con el maximo |
|  |  | Razón social | si | Alfa numerico | No | maximo 150 carcateres |  | Control en front con el maximo |
|  |  | Botón para adjuntar archivo Adjuntar RUT | si | Varchar | si | maximo 500 caracteres | Archivo permitido PDF peso máximo 100 MB | Control tipo de archivo |
|  |  | Número de teléfono | si | Numerico | si | Minimo 10 - maximo 50 caracteres |  | Front se debe ver quemado el +57 siempre colombia |
|  |  | Correo electrónico | si | Varchar | si | maximo 150 carcateres |  | Control en Front de correo, validar el @ |
|  |  | Dirección | si | Varchar | No | maximo 200 caracteres |  | Crontrol en front de prefijos de carrera, avenida y demas |
|  |  | Departamento Lista desplegable | si | Lista desplegable | No |  |  | ya esta |
|  |  | Ciudad /Municipio Lista desplegable | si | Lista desplegable | No |  |  | ya esta |
|  |  | ¿Tiene sucursales con el mismo NIT? | si | Casillas tipo check “NO” y “SI” |  |  |  | N/A |
|  | HU 01 – Gestión de talleres registro sucursales | Correo electrónico | si | Varchar | si | maximo 150 carcateres |  | Control en Front de correo, validar el @ |
|  |  | Dirección | si | Varchar | No | maximo 200 caracteres |  | Crontrol en front de prefijos de carrera, avenida y demas |
|  |  | Departamento | si | Lista desplegable | No |  |  | ya esta |
|  |  | Ciudad /Municipio | si | Lista desplegable | No |  |  | ya esta |
| 2 | HU 01.1 – Gestión de Certificadores | Tipo de identificación | si | Lista desplegable | No |  |  | NIT |
|  |  | Numero de documento | si | Numerico | No | maximo 50 caracteres |  | Control en front con el maximo |
|  |  | Nombre comercial | si | Alfa numerico | No | maximo 150 carcateres |  | Control en front con el maximo |
|  |  | Razón social | si | Alfa numerico | No | maximo 150 carcateres |  | Control en front con el maximo |
|  |  | Botón para adjuntar archivo Adjuntar RUT | si | Varchar | si | maximo 500 caracteres | Archivo permitido PDF peso máximo 100 MB | Control tipo de archivo |
|  |  | Número de teléfono | si | Numerico | si | Minimo 10 - maximo 50 caracteres |  | Front se debe ver quemado el +57 siempre colombia |
|  |  | Correo electrónico | si | Varchar | si | maximo 150 carcateres |  | Control en Front de correo, validar el @ |
| 3 | HU 01.2 – Gestión de usuarios | Tipo de rol | si | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Tipo de identificación | si | Lista desplegable | No |  |  | las opciones seran NIT, cedula, cedula extranjeria |
|  |  | Numero de documento | si | Numerico | No | maximo 50 caracteres |  | Control en front con el maximo |
|  |  | Nombre | si | Varchar | si | maximo 150 carcateres |  |  |
|  |  | Número de teléfono | si | Numerico | si | Minimo 10 - maximo 50 caracteres |  | Front se debe ver quemado el +57 siempre colombia |
|  |  | Correo electrónico | si | Varchar | si | maximo 150 carcateres |  | Control en Front de correo, validar el @ |
| 4 | HU 01.3 – Gestión de roles | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 5 | HU 02 – Cargue individual de placas vencidos | Placa | si | Campo tipo texto * | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | M3 en mora* | si | Decimal |  | Maximo 10,2 caracteres |  | Crontrol en front de ingreso de decimales |
|  |  | Observaciones | No | Campo tipo texto libre |  | 100 caracteres |  |  |
|  | HU 02 – Cargue individual de placas Altos consumidores | Placa | si | Campo tipo texto * | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Consumo ultimo año | si | Decimal |  | Maximo 10,2 caracteres |  | Crontrol en front de ingreso de decimales |
|  |  | Observaciones | No | Campo tipo texto libre |  | 100 caracteres |  |  |
|  | HU 02 – Cargue individual de placas Puesta punto | Placa | si | Campo tipo texto * | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Observaciones | No | Campo tipo texto libre |  | 100 caracteres |  |  |
| 6 | HU 02.1 – Cargue masivo de placas | Botón “Adjuntar archivo” | si | Varchar |  | 500 caracteres maximos | peso maximo 700 mb | Crontrol en front solo archivos en formato permitido y tamaño |
| 7 | HU 03 edicion vencidos y altos consumidores | Placa | N/A | Campo tipo texto | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Consumo ultimo año | N/A | Decimal | si | Maximo 10,2 caracteres |  | Crontrol en front de ingreso de decimales |
|  |  | Observaciones | N/A | Campo tipo texto | si | 100 caracteres |  |  |
|  | HU 03 edicion Puesta punto | Placa | N/A | Campo tipo texto | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Observaciones | N/A | Campo tipo texto | si | 100 caracteres |  |  |
| 8 | HU 03.1 registro puesta punto | Placa* | si | campo tipo texto | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Marca del vehículo* | si | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Línea del servicio | si | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Origen de la falla* | si | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | botón para adjuntar archivos | No | Varchar | No | 500 caracteres maximos | tamaño maximo 100 mb | Crontrol en front solo archivos en formato permitido y tamaño |
|  |  | Observaciones | No | campo tipo texto | No | 100 caracteres |  |  |
|  | HU 03.1 registro altos consumidores y vencidos | Placa* | si | campo tipo texto | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Tipo de vehículo | No | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Mes/año | No | calendario de mes y año | No |  |  |  |
|  |  | Tipo de revisión* | si | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Fecha de certificación* | si | campo calendario | No |  |  |  |
|  |  | N° Cilindros vehículos* | si | Campo numérico | No | 2 caracteres maximo |  | ya se valido |
|  |  | N° cilindros revisados* | si | Campo numérico | No | 2 caracteres maximo |  | ya se valido |
|  |  | Ente certificador* | si | Lista desplegable | No |  |  | Lo suministra negocio |
| 9 | HU 03.2 | N/A | N/A | N/A | N/A | N/A | N/A |  |
| 10 | HU 03.3 puesta punto | Placa | N/A | Campo tipo texto * | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Marca del vehículo | N/A | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Línea del servicio | N/A | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Origen de la falla | N/A | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Botón “Adjuntar archivos” Permite archivos JPG, PNG o PDF | N/A | Varchar | si | 500 caracteres maximos | tamaño maximo 100 mb | Control tipo de archivo |
|  |  | Observaciones | N/A | campo tipo texto | si | Máximo 200 caracteres |  |  |
|  | HU 03.3 Vencidos y altos consumidores | Placa | N/A | Campo tipo texto * | No | maximo 17 caracteres |  | Control en front de solo ingreso de caracteres alfanumericos |
|  |  | Tipo del vehículo | N/A | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Mes/año No editable | N/A | campo tipo calendario | No |  |  |  |
|  |  | Tipo de revisión “quinquenal o anual” | N/A | Lista desplegable | Si |  |  | Lo suministra negocio |
|  |  | Fecha de certificación | N/A | campo tipo calendario | si |  |  |  |
|  |  | N° cilindros vehículos | N/A | Campo numérico | No | 2 caracteres maximo |  | ya se valido |
|  |  | N° cilindros revisados | N/A | Campo numérico | No | 2 caracteres maximo |  | ya se valido |
|  |  | Ente certificador | N/A | Lista desplegable | si |  |  | Lo suministra negocio |
| 11 | HU 04 | Campo tipo texto con la siguiente descripción “Escribir razón por la que cambia el estado” | si | campo tipo texto | No | Máximo 100 caracteres |  |  |
| 12 | HU 04.1 | Campo tipo texto con la siguiente descripción “Escribir razón por la que cambia el estado” | si | campo tipo texto | No | Máximo 100 caracteres |  |  |
| 13 | HU 04.2 | N/A | N/A | N/A | N/A | N/A | N/A |  |
| 14 | HU 05 | N/A | N/A | N/A | N/A | N/A | N/A |  |
| 15 | HU 05.1 | N/A | N/A | N/A | N/A | N/A | N/A |  |
| 16 | HU 06 Dsitribucion placas | Programa | No | Lista desplegable | N/A | N/A | N/A | ya esta |
|  |  | Año | No | Lista desplegable | N/A | N/A | N/A | ya esta |
|  |  | Mes | No | Lista desplegable | N/A | N/A | N/A | ya esta |
| 17 | HU 06.1 Dsitribucion de presupuesto | Programa | No | Lista desplegable | N/A | N/A | N/A | ya esta |
|  |  | Año | No | Lista desplegable | N/A | N/A | N/A | ya esta |
|  |  | Mes | No | Lista desplegable | N/A | N/A | N/A | ya esta |
|  |  | Presupuesto Anual | No | Decimal | si | 15,2 caracteres maximos |  | Crontrol en front de ingreso de decimales |
| 18 | HU 06.2 Indicadores certificadores y talleres | Ente certificador | Si | Campo de busqueda |  | maximo 50 carcateres |  | ya se valido |
|  |  | Programa | No | Lista desplegable |  |  |  | ya esta |
|  |  | Año | No | Lista desplegable |  |  |  | ya esta |
|  |  | Mes | No | Lista desplegable |  |  |  | ya esta |
|  |  | Taller | Si | Campo de busqueda |  | maximo 50 carcateres |  | ya se valido |
|  |  | Programa | No | Lista desplegable |  |  |  | ya esta |
|  |  | Mes | No | Lista desplegable |  |  |  | ya esta |
| 19 | HU 07 | Cuadro de texto | no | campo tipo texto |  | 20 caracteres maximos |  |  |
| 20 | HU 10 | Regional | si | Lista desplegable | si |  |  | ya esta |
|  |  | Ciudad | si | Lista desplegable | si |  |  | ya esta |
|  |  | Costo revisión | si | decimal | si | maximo 12,2 |  | Crontrol en front de ingreso de decimales |
|  |  | Costo cilindro adicional | si | Entero | si | Maximo 10 caracteres |  | Control en front de ingresdo de enteros |
|  |  | Costo revisión | si | decimal | si | maximo 12,2 |  | Crontrol en front de ingreso de decimales |
|  |  | Costo cilindro adicional | si | Entero | si | Maximo 10 caracteres |  | Control en front de ingresdo de enteros |
|  |  | Fecha inicio de vigencia | si | campo tipo calendario | si |  |  |  |
|  |  | Fecha fin de vigencia | si | campo tipo calendario | si |  |  |  |
|  |  | Mensaje “Registrar valores antes de IVA” | Si |  |  |  |  | Se debe visualizar en front |
|  |  | Botón Adjuntar archivo | si | Varchar | si | 500 caracteres maximos | tamaño máximo de 100mb |  |
| 21 | HU 10.1 | Certificador | Si | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Ciudad | Si | Lista desplegable | si |  |  | ya esta |
|  |  | Costo certificación de revisión | Si | Entero | si | Maximo 10 caracteres |  | Control en front de ingresdo de enteros |
|  |  | Fecha inicio de vigencia | Si | campo tipo calendario | si |  |  |  |
|  |  | Fecha fin de vigencia | Si | campo tipo calendario | si |  |  |  |
|  |  | Botón Adjuntar archivo | si | Varchar | si | 500 caracteres maximos | tamaño máximo de 100mb |  |
| 22 | HU 10.2 | Origen de la falla | si | Lista desplegable | si |  |  | Lo suministra negocio |
|  |  | Descripción | no | campo tipo texto | si | 50 caracteres máximos |  |  |
|  |  | Costo | si | decimal | si | maximo 12,2 |  | Crontrol en front de ingreso de decimales |
| 23 | HU 11 | Rol | N/A | Lista desplegable | No |  |  | Lo suministra negocio |
|  |  | Nombre | N/A | campo tipo texto | No |  |  |  |
|  |  | Número de teléfono | N/A | Numerico | si | Minimo 10 - maximo 50 caracteres |  | Front se debe ver quemado el +57 siempre colombia |
|  |  | Correo electrónico | N/A | Varchar | si | maximo 150 carcateres |  | Control en Front de correo, validar el @ |
|  | HU 11 actualizacion de contraseña | Contraseña actual | si |  |  |  |  | Integracion con CIAM |
|  |  | Contraseña nueva | si |  |  |  |  | Integracion con CIAM |
|  |  | Confirmacion de contraseña | si |  |  |  |  | Integracion con CIAM |
