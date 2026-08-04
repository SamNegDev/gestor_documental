# Hoja de ruta de mejoras

Documento vivo para conservar el contexto de evolucion del sistema.

## Regla de mantenimiento

- Antes de proponer una mejora, revisar esta hoja para evitar duplicados.
- Cuando una mejora quede implantada y verificada, eliminarla de pendientes.
- Anotar nuevas ideas en el bloque que corresponda.
- Priorizar reduccion de trabajo manual, seguridad, trazabilidad y escalabilidad.
- Mantener separado el historial funcional visible de la auditoria interna: los accesos, descargas y operaciones tecnicas sensibles no deben ensuciar la experiencia del usuario.

## Cierre de version para presentacion oficial

### Integridad y estabilizacion

- Ejecutar una prueba funcional completa con perfiles ADMIN y CLIENTE: solicitud, carga y separacion documental, lectura IA, preparacion de PDF, creacion de expediente, requisitos, incidencias, hitos, justificantes y facturacion.
- Revisar logs, tareas automaticas, copias de seguridad y restauracion; corregir cualquier error antes de fijar la version candidata a presentacion.

### Activaciones controladas en produccion

- Reactivar gradualmente el envio programado de avisos por correo: comenzar con un unico cliente y limites de un envio por ejecucion y por dia, revisar destinatarios y registros, y ampliar solo tras validar el comportamiento.
- Configurar las credenciales y el webhook de Holded en produccion; validar una sincronizacion manual y un evento real antes de habilitar la sincronizacion periodica.
- Configurar y validar plantillas de WhatsApp aprobadas por Meta para iniciar avisos fuera de la ventana de 24 horas.

### Preparacion de la presentacion

- Preparar datos de demostracion anonimizados y cuentas de prueba ADMIN y CLIENTE que permitan recorrer el flujo completo sin tocar expedientes reales.
- Actualizar los manuales de usuario y administrador, preparar un guion de demostracion y documentar las funcionalidades principales y sus limites actuales.
- Publicar una version identificable, con notas de version, fecha, copia de seguridad previa y procedimiento de vuelta atras verificado.

## Seguridad antes de apertura general

- Segundo factor para administradores.
- Gestion avanzada de sesiones y dispositivos activos.
- Politica de conservacion y eliminacion documental.
- Revisar proteccion de datos personales, permisos, exportaciones, copias de seguridad y registros de auditoria antes de ampliar el acceso a nuevos clientes.

## Mejoras posteriores al cierre

### Plantillas documentales

- Incorporar nuevos modelos oficiales cuando sean necesarios.
- Modelar el precio como dato estructurado y reutilizarlo en plantillas, facturacion y extracciones donde corresponda.

### Gestion documental avanzada

- Extender la edicion de administradores ya disponible en la ficha de cliente a expediente y ficha de interesado; migrar las marcas genericas existentes despues de revision.
- Registrar decisiones validadas en revision GA como memoria auxiliar trazable para reutilizar correcciones de personas, direcciones y vehiculos en futuras extracciones.
- Versiones de documentos.
- Deteccion de duplicados.
- Fechas de caducidad documental.

### Registro ampliado de vehiculos

- Auditar inconsistencias entre la matricula del expediente y la ficha consolidada del vehiculo.
