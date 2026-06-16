# Plan definitivo de integracion WhatsApp en GestApp

Este documento define la evolucion de WhatsApp dentro de GestApp como canal operativo conectado al sistema documental, no como una bandeja aislada. El objetivo es que WhatsApp ayude a reducir llamadas, acelerar respuestas de clientes, registrar trazabilidad y convertir mensajes en acciones reales dentro de expedientes, seguimientos y tareas.

No se incluyen numeros de prueba, tokens, identificadores temporales ni credenciales. La configuracion sensible debe mantenerse siempre en variables de entorno de produccion.

## Vision del canal

WhatsApp debe funcionar como una extension guiada de GestApp. El cliente interactua desde una interfaz sencilla, con mensajes, botones, listas y enlaces seguros. GestApp interpreta cada respuesta, la vincula al cliente o expediente correspondiente y crea trabajo interno cuando sea necesario.

El canal no debe sustituir al portal web. WhatsApp debe resolver acciones rapidas y conducir al cliente al portal cuando haya que consultar, subir o revisar informacion sensible.

## Principios de diseno

- WhatsApp se usa para avisos, recordatorios, respuestas rapidas y recepcion inicial de documentacion.
- GestApp sigue siendo la fuente principal de verdad para expedientes, documentos, tareas y trazabilidad.
- Cada mensaje recibido debe tener estado operativo: pendiente, revisado, asociado, convertido en tarea o archivado.
- Las acciones automaticas deben derivarse del estado real del sistema.
- Los datos sensibles deben minimizarse en WhatsApp y completarse mediante enlaces seguros a GestApp.
- Todo envio saliente debe quedar registrado igual que los avisos por correo.
- Toda respuesta relevante debe poder verse desde el expediente asociado.

## Flujo general

```text
Cliente recibe aviso o escribe por WhatsApp
        |
Meta envia evento al webhook de GestApp
        |
GestApp guarda el mensaje y lo asocia si reconoce el telefono
        |
GestApp interpreta texto, boton, lista o adjunto
        |
GestApp crea accion interna, tarea, seguimiento o respuesta
        |
El administrador revisa o confirma cuando haga falta
```

## Fase base ya implantada

GestApp ya dispone de la base tecnica para recibir mensajes:

- Webhook publico conectado con Meta.
- Validacion de firma mediante secreto de la app.
- Recepcion de mensajes reales.
- Bandeja interna de WhatsApp para administradores.
- Filtros por mensajes asociados, no asociados y errores.
- Asociacion manual a cliente y expediente.
- Limpieza de eventos tecnicos para no llenar la bandeja con estados vacios.

Esta base permite empezar a construir automatismos sin perder control operativo.

## Fase de orden operativo

El siguiente bloque debe convertir la bandeja en una herramienta diaria de trabajo.

Alcance recomendado:

- Estado del mensaje: pendiente, revisado, archivado.
- Filtro principal de pendientes.
- Boton para marcar como revisado.
- Boton para convertir mensaje en tarea.
- Tarea automatica cuando entra un mensaje asociado a expediente.
- Visualizacion de mensajes WhatsApp dentro del expediente.
- Registro en historial cuando un mensaje se atiende o se convierte en accion.

Resultado esperado:

El administrador no solo ve mensajes; puede gestionarlos, cerrarlos y relacionarlos con trabajo real.

## Fase de avisos por WhatsApp

Esta fase conecta WhatsApp con el sistema de seguimiento y vencimientos.

Alcance recomendado:

- Boton "Enviar WhatsApp" junto al aviso por correo.
- Vista previa del mensaje antes de enviar.
- Registro del aviso en el historial de avisos.
- Contador de avisos compartido o diferenciado por canal, segun decision funcional.
- Plantillas aprobadas por Meta para iniciar conversaciones fuera de la ventana de 24 horas.
- Botones en los avisos para recoger respuestas utiles.

Plantillas iniciales recomendadas:

- Documentacion pendiente.
- Recordatorio de documentacion.
- Solicitud de informacion adicional.
- Aviso de expediente actualizado.

Botones recomendados:

- Enviar documentacion.
- Ya lo envie.
- Recordarme otro dia.
- Contactar con gestoria.

Resultado esperado:

Los avisos dejan de depender solo del correo y pasan a un canal con mayor tasa de respuesta.

## Fase de acciones desde botones

Los botones de WhatsApp deben ejecutar acciones concretas dentro de GestApp.

Acciones recomendadas:

- "Ya lo envie" crea tarea de revision para el administrador.
- "Recordarme otro dia" pospone el seguimiento a una fecha predefinida o solicita fecha.
- "Enviar documentacion" devuelve un enlace seguro al portal o inicia un flujo de recepcion por WhatsApp.
- "Contactar con gestoria" crea tarea de llamada o contacto.
- "Ver expediente" envia un enlace seguro al portal del cliente.

Estas acciones no deben cerrar expedientes ni resolver incidencias de forma automatica sin revision humana. Deben preparar el trabajo para que el administrador decida.

## Fase de menu guiado para clientes

Cuando el cliente escribe de forma espontanea, GestApp puede responder con un menu principal.

Opciones recomendadas:

- Consultar mis expedientes.
- Enviar documentacion.
- Ver documentacion pendiente.
- Hablar con la gestoria.

El menu debe estar limitado a clientes identificados por telefono. Si el telefono no esta asociado, GestApp debe pedir identificacion minima o crear una tarea para asociacion manual.

Resultado esperado:

WhatsApp se convierte en una puerta de entrada sencilla para clientes, sin obligarles a recordar rutas o procesos.

## Fase de recepcion de adjuntos

Esta fase permite recibir fotos, PDFs y documentos enviados por WhatsApp.

Alcance recomendado:

- Descargar adjuntos desde Meta Cloud API.
- Guardarlos como documentos entrantes.
- Asociarlos a cliente y expediente si el contexto es claro.
- Crear bandeja de documentos WhatsApp pendientes de clasificar.
- Permitir convertir adjunto en documento del expediente.
- Registrar origen, telefono, fecha y mensaje relacionado.

Reglas recomendadas:

- Si el cliente estaba en un flujo de "Enviar documentacion", usar ese contexto.
- Si no hay contexto, guardar como documento pendiente de clasificar.
- Si el telefono no esta asociado, guardar sin vincular y crear tarea de revision.

Resultado esperado:

La gestoría puede recibir documentacion por WhatsApp sin perder trazabilidad ni mezclar archivos manualmente.

## Fase de preferencia de canal

Cada cliente debe poder tener una preferencia de comunicacion.

Opciones recomendadas:

- Email.
- WhatsApp.
- Ambos.
- Sin avisos automaticos.

Esta preferencia debe aplicarse a recordatorios, vencimientos y avisos manuales.

Resultado esperado:

GestApp adapta los avisos al canal mas efectivo para cada cliente.

## Fase de enlaces seguros

WhatsApp debe poder llevar al cliente a acciones concretas dentro del portal.

Casos recomendados:

- Ver expediente.
- Subir documentacion pendiente.
- Responder a una solicitud.
- Consultar estado.

Los enlaces deben ser seguros, con caducidad y alcance limitado. No deben dar acceso general sin autenticacion o sin token temporal controlado.

Resultado esperado:

WhatsApp no expone informacion sensible, sino que dirige al cliente a GestApp cuando la accion lo requiere.

## Fase de trazabilidad completa

Toda comunicacion por WhatsApp debe quedar visible en el contexto adecuado.

Ubicaciones recomendadas:

- Bandeja WhatsApp general.
- Historial del expediente.
- Seguimiento del cliente.
- Tareas relacionadas.
- Registro de avisos enviados.

Eventos a registrar:

- Mensaje recibido.
- Mensaje revisado.
- Asociacion manual.
- Aviso enviado.
- Boton pulsado.
- Tarea creada.
- Documento recibido.
- Seguimiento pospuesto.

Resultado esperado:

El equipo puede reconstruir que se pidio, cuando se pidio, como respondio el cliente y que accion interna se tomo.

## Fase de automatizacion avanzada

Cuando el canal sea estable, pueden incorporarse automatismos mas inteligentes.

Opciones recomendadas:

- Clasificacion de intencion del mensaje.
- Deteccion de respuestas como "lo envio mañana", "ya lo mande" o "llamame".
- Sugerencia de posponer seguimiento.
- Sugerencia de crear tarea.
- Resumen de conversacion en expediente.
- Alertas si un cliente envia documentacion fuera de contexto.

Estas acciones deben empezar como sugerencias o tareas pendientes, no como cierres automaticos.

## Primer paso recomendado

El primer paso debe ser la fase de orden operativo.

Motivo:

Ya recibimos mensajes y ya existe bandeja. Antes de enviar mas avisos o crear menus, necesitamos que cada mensaje entrante tenga un ciclo claro de gestion. Si no, WhatsApp puede generar ruido operativo.

Trabajo concreto del primer paso:

- Añadir estado pendiente/revisado/archivado al mensaje WhatsApp.
- Crear filtro de pendientes como vista principal.
- Añadir boton "Marcar revisado".
- Crear tarea automatica cuando entre un mensaje asociado a expediente.
- Mostrar mensajes WhatsApp dentro del expediente asociado.

Resultado esperado:

Cada respuesta del cliente por WhatsApp se convierte en una unidad de trabajo controlada, visible y trazable.

## Decision funcional pendiente

Antes de implementar el envio de avisos por WhatsApp conviene decidir como computan los avisos:

- Opcion recomendada: email y WhatsApp cuentan dentro del mismo ciclo de avisos, porque ambos son comunicaciones al cliente sobre el mismo seguimiento.
- Alternativa: mantener contador por canal, si se quiere medir efectividad separada.

La opcion recomendada evita duplicar presion sobre el cliente y mantiene simple el vencimiento.

## Riesgos a controlar

- No enviar datos sensibles completos por WhatsApp.
- No crear automatismos que cierren incidencias sin revision humana.
- No depender de numeros de prueba para funcionalidad real.
- No guardar tokens ni secretos en repositorio.
- No llenar la bandeja con eventos tecnicos de Meta.
- No permitir que mensajes sin asociar ejecuten acciones sobre expedientes.

## Resultado final esperado

WhatsApp debe quedar integrado como un canal completo:

- Recibe mensajes.
- Identifica cliente.
- Vincula expediente.
- Genera tareas.
- Recibe documentos.
- Envia avisos.
- Permite botones y menus.
- Registra trazabilidad.
- Conduce al cliente al portal cuando la accion requiere seguridad.

La web sigue siendo el sistema principal. WhatsApp se convierte en la interfaz rapida y cercana para activar respuestas del cliente.
