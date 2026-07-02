# Hoja de ruta de mejoras

Documento vivo para conservar el contexto de evolucion del sistema.

## Regla de mantenimiento

- Antes de proponer una mejora, revisar esta hoja para evitar duplicados.
- Cuando una mejora quede implantada y verificada, eliminarla de pendientes.
- Anotar nuevas ideas en el bloque que corresponda.
- Priorizar reduccion de trabajo manual, seguridad, trazabilidad y escalabilidad.

## Pendientes prioritarios

### Notificaciones y recordatorios

- Automatizar el envio programado de avisos por correo cuando falte documentacion o respuesta del cliente.
- Completar integracion WhatsApp: envio saliente por plantillas aprobadas y seleccion de canal preferido por cliente.

### Ampliacion del historial documental

- Completar el historial existente con consultas, descargas y eliminaciones de documentos que aun no queden registradas.
- Conservar valores anteriores y posteriores solo en cambios relevantes donde aporte trazabilidad adicional.
- Permitir exportar el historial ya existente de un expediente.

## Mejoras operativas

### Plantillas documentales

- Completar la interfaz guiada de preparacion de traspaso en solicitud usando el estado backend del asistente, con acciones directas para corregir datos y generar documentos.
- Incorporar nuevos modelos oficiales cuando sean necesarios.
- Completar automaticamente marca, modelo y bastidor desde el registro ampliado de vehiculos, e incorporar precio cuando quede modelado como dato estructurado.

### Gestion documental avanzada

- Evaluar extraccion IA de datos FORMATO_GA sobre documentacion relevante del expediente, con validacion humana antes de persistir.
- Guardar datos estructurados validados de clientes y representantes para reutilizarlos sin nueva extraccion IA.
- Registrar decisiones validadas en revision GA como memoria auxiliar trazable para reutilizar correcciones de personas, direcciones y vehiculos en futuras extracciones.
- Importar catalogo historico de personas de Gestion Trafico como base auxiliar de autocompletado y deduplicacion.
- Importar catalogo historico de vehiculos de Gestion Trafico como base auxiliar de cotejo por matricula, bastidor y modelo.
- Versiones de documentos.
- Deteccion de duplicados.
- Fechas de caducidad documental.

### Registro ampliado de vehiculos

- Auditar inconsistencias entre la matricula del expediente y la ficha consolidada del vehiculo.

## Seguridad

- Segundo factor para administradores.
- Gestion avanzada de sesiones y dispositivos activos.
- Politica de conservacion y eliminacion documental.
- Proteccion adicional de datos personales.
- Registro de accesos y descargas sensibles.
