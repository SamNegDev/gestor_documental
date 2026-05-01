# Manual de administrador

## 1. Introducción

Este manual explica el uso de la aplicación desde el perfil administrador.

El administrador representa al personal interno de la gestoría y puede gestionar clientes, usuarios, solicitudes, expedientes, documentación, incidencias, mensajes e historial.

## 2. Acceso a la aplicación

Para acceder:

1. Abre la URL de la aplicación.
2. Introduce el email y la contraseña de administrador.
3. Pulsa el botón de inicio de sesión.

Si las credenciales son correctas, accederás al dashboard de administración.

En la parte superior derecha aparece el usuario conectado y el botón `Cerrar sesión`.

## 3. Menú principal

El menú lateral del administrador contiene estas opciones:

- `Dashboard`
- `Solicitudes`
- `Expedientes`
- `Usuarios`
- `Clientes`

## 4. Dashboard

El dashboard ofrece una vista resumida del estado del sistema.

Desde esta pantalla se puede acceder rápidamente a:

- Crear un nuevo expediente.
- Ver el listado de expedientes.
- Ver el listado de solicitudes.
- Consultar expedientes y solicitudes recientes.

Es la pantalla recomendada para revisar la actividad general de la gestoría.

## 5. Gestión de solicitudes

La sección `Solicitudes` muestra las solicitudes creadas por los clientes.

El listado permite filtrar por:

- Cliente.
- Estado.
- Tipo de trámite.
- Matrícula.

Desde cada solicitud se puede acceder al detalle mediante el botón `Ver`.

En el detalle de una solicitud el administrador puede:

- Revisar la información general.
- Consultar interesados.
- Ver el historial.
- Leer y enviar mensajes.
- Consultar incidencias.
- Ver, descargar y gestionar documentos.
- Pedir documentación.
- Marcar una incidencia.
- Rechazar la solicitud.
- Convertir la solicitud en expediente.

## 6. Estados de solicitud

Estados disponibles:

- `PENDIENTE_REVISION`: solicitud pendiente de revisión.
- `PENDIENTE_DOCUMENTACION`: se necesita documentación o corrección del cliente.
- `REVISANDO_INCIDENCIAS`: el cliente ha solicitado nueva revisión.
- `CONVERTIDA`: la solicitud ya se ha convertido en expediente.
- `RECHAZADO`: la solicitud ha sido rechazada.

## 7. Convertir solicitud en expediente

Cuando una solicitud está correcta:

1. Entra en el detalle de la solicitud.
2. Revisa datos, interesados y documentación.
3. Pulsa `Convertir a expediente`.
4. Confirma la acción.

Al convertirla:

- Se crea un expediente asociado.
- La solicitud queda marcada como `CONVERTIDA`.
- Se mantiene la relación entre solicitud y expediente.
- Desde la solicitud puede consultarse el expediente generado.

## 8. Gestión de expedientes

La sección `Expedientes` muestra los expedientes creados en el sistema.

El listado permite filtrar por:

- Cliente.
- Estado.
- Tipo de trámite.
- Matrícula.

Desde el detalle de un expediente el administrador puede:

- Consultar la información general.
- Ver el cliente asociado.
- Ver el tipo de trámite y matrícula.
- Comprobar si procede de una solicitud o de creación directa.
- Revisar interesados.
- Consultar historial.
- Leer y enviar mensajes.
- Subir documentación.
- Ver, descargar, editar o eliminar documentos.
- Extraer páginas de un PDF.
- Cambiar el estado del expediente.
- Marcar incidencias.
- Resolver incidencias.
- Editar el expediente si no está finalizado.

## 9. Estados de expediente

Estados disponibles:

- `EN_TRAMITE`: expediente en curso.
- `INCIDENCIA`: expediente con incidencia pendiente.
- `REVISANDO_INCIDENCIAS`: el cliente ha respondido y se está revisando.
- `FINALIZADO`: expediente terminado.
- `RECHAZADO`: expediente rechazado.
- `ENVIADO_DGT`: expediente enviado a DGT.

## 10. Crear expediente directamente

El administrador puede crear un expediente sin partir de una solicitud.

Pasos:

1. Entra en `Expedientes`.
2. Pulsa `Nuevo expediente`.
3. Selecciona el cliente.
4. Selecciona el tipo de trámite.
5. Indica la matrícula.
6. Completa los interesados si procede.
7. Añade documentación disponible.
8. Añade observaciones si son necesarias.
9. Pulsa `Guardar expediente`.

Si se sube un documento marcado como `EXPEDIENTE_COMPLETO`, la aplicación puede procesarlo mediante OCR. Durante este proceso aparecerá una pantalla de carga.

## 11. Gestión documental

En solicitudes y expedientes existe un bloque de documentación.

El administrador puede:

- Subir documentos.
- Elegir el tipo documental.
- Ver documentos en el navegador.
- Descargar archivos.
- Editar el nombre del archivo.
- Cambiar el tipo documental.
- Eliminar documentos.
- Extraer páginas de un PDF para crear un nuevo documento.

Tipos documentales disponibles:

- DNI.
- Contrato de compraventa.
- Permiso de circulación.
- Ficha técnica.
- Mandato.
- Factura.
- Expediente completo.
- Mandato de representación.
- Cambio de titularidad.
- Autorización Serafín.
- Huella de trámite.
- Otros.

## 12. Extracción de páginas de PDF

La opción `Extraer Páginas` permite crear un documento nuevo a partir de páginas concretas de un PDF.

Formato admitido:

```text
1, 3, 5-7
```

Al usar esta opción se debe indicar:

- Páginas a extraer.
- Nombre del nuevo archivo.
- Tipo documental del nuevo documento.

Esta función es útil cuando un único PDF contiene varios documentos mezclados.

## 13. Incidencias

Las incidencias sirven para comunicar errores, falta de documentación o necesidad de corrección.

Para crear una incidencia:

1. Entra en el detalle de una solicitud o expediente.
2. Pulsa `Marcar Incidencia`.
3. Selecciona el tipo de incidencia.
4. Escribe las observaciones.
5. Pulsa `Confirmar Incidencia`.

El cliente podrá ver la incidencia, aportar documentación y solicitar una nueva revisión.

El administrador puede resolver incidencias manualmente desde el bloque de incidencias.

## 14. Mensajes

Solicitudes y expedientes incluyen una zona de mensajes.

Desde ella el administrador puede:

- Leer mensajes del cliente.
- Enviar aclaraciones.
- Mantener la comunicación vinculada al trámite correspondiente.

Esto ayuda a evitar que la información quede dispersa en correo electrónico, WhatsApp u otros canales.

## 15. Historial

El historial muestra acciones y cambios relevantes realizados sobre solicitudes o expedientes.

Permite revisar la evolución del trámite y comprobar qué ha ocurrido durante su gestión.

## 16. Gestión de clientes

La sección `Clientes` permite administrar los clientes de la gestoría.

Desde esta pantalla se puede:

- Ver el listado de clientes.
- Crear un nuevo cliente.
- Editar un cliente existente.
- Eliminar clientes cuando el sistema lo permita.

Datos principales de cliente:

- NIF/CIF.
- Nombre o razón social.
- Email.
- Teléfono.
- Dirección.

## 17. Gestión de usuarios

La sección `Usuarios` permite administrar las cuentas de acceso.

Desde esta pantalla se puede:

- Ver el listado de usuarios.
- Crear usuarios.
- Editar usuarios existentes.
- Activar o desactivar usuarios.
- Asociar un usuario con rol `CLIENTE` a un cliente.
- Eliminar usuarios inactivos.

Datos principales de usuario:

- Nombre.
- Apellidos.
- Email.
- Contraseña.
- Rol de usuario.
- Cliente asignado, en caso de usuarios cliente.
- Estado activo o inactivo.

Al editar un usuario, la contraseña puede dejarse en blanco para mantener la actual.

## 18. Flujo recomendado de trabajo

Flujo habitual:

1. Revisar nuevas solicitudes.
2. Comprobar datos, interesados y documentación.
3. Pedir documentación o crear incidencias si falta información.
4. Convertir solicitudes correctas en expedientes.
5. Gestionar el expediente hasta su finalización.
6. Mantener mensajes, historial y documentación centralizados.

## 19. Recomendaciones

- Revisa siempre la documentación antes de convertir una solicitud.
- Usa incidencias para dejar constancia clara de errores o documentos pendientes.
- Mantén los documentos correctamente clasificados.
- Usa los mensajes internos para conversaciones relacionadas con el trámite.
- No cierres la página durante el procesamiento OCR.
- Evita almacenar documentación duplicada salvo que sea necesario conservar versiones.
- Desactiva usuarios que no deban acceder antes de eliminarlos.
