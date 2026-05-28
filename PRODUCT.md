# PRODUCT.md

Fecha de actualizacion: 2026-05-20

## Register

Product.

Gestoria Portal es una herramienta interna de gestion documental y tramitacion administrativa. El diseno debe servir al trabajo diario: claridad, confianza, rapidez de lectura y trazabilidad.

## Usuarios

- Gestores administrativos que revisan expedientes, preparan documentacion y avanzan tramites.
- Administradores que necesitan supervision operativa, control de estados y seguimiento de incidencias.
- Clientes o usuarios externos pueden existir en el dominio, pero no son el foco principal del primer frontend React.

## Proposito del producto

Centralizar expedientes, documentos, comunicaciones y estados de tramitacion para que un gestor pueda saber rapidamente:

- En que fase esta cada expediente.
- Que accion toca ahora.
- Que falta para poder avanzar.
- Si hay incidencias, bloqueos o respuestas pendientes.
- Donde consultar el historial cuando haga falta.

## Principios de producto

- El expediente se entiende como una tramitacion lineal, no como una coleccion de tareas sueltas.
- La informacion del expediente y su documentacion tienen prioridad sobre la visualizacion de fases.
- La fase actual y el siguiente paso deben estar claros, pero las fases no deben desplazar la ficha operativa del expediente.
- La trazabilidad debe estar siempre disponible, pero no debe competir con el trabajo principal.
- La interfaz debe ser sobria, moderna y densa, pensada para uso repetido en escritorio.
- El esquema visual debe apoyarse en los colores corporativos azul turquesa y amarillo, adaptados a una paleta de producto sobria y accesible.
- El producto debe sentirse familiar para usuarios de herramientas profesionales como Linear, Figma, Notion o Stripe: componentes previsibles, buena jerarquia y pocas sorpresas.
- Evitar ornamentacion que no ayude a decidir o avanzar el tramite.

## Flujo base de expediente

El modelo inicial de expediente se articula en tres fases:

1. Expediente creado
2. Tramitando expediente
3. Tramitado / Incidencias

Cuando un expediente ya ha sido creado, se sobreentiende que antes fue revisado como solicitud y que la documentacion base es correcta.

La tercera fase puede representar varios estados finales u operativos:

- Tramitado.
- Pendiente de respuesta.
- Incidencia abierta.
- Subsanacion requerida.

Si aparece una incidencia, el flujo puede incorporar movimientos posteriores hasta volver a un punto final: incidencia, subsanacion, revision y tramitado.

## Detalle operativo de fase

Para la vista de expediente, la fase puede desglosarse inicialmente en estos hitos:

1. Solicitud revisada y convertida en expediente
2. Documentacion completa
3. Tramite subido en programa gestion
4. Modelo 620 presentado
5. Finalizado / Incidencia

Los checks de estos hitos se comportan como un flujo secuencial:

- Solicitud revisada y convertida en expediente: automatico al crear el expediente desde una solicitud validada.
- Documentacion completa: automatico cuando todos los documentos base requeridos estan subidos y validados.
- Tramite subido en programa gestion: manual, con referencia, fecha o nota interna.
- Modelo 620 presentado: paso posterior, manual o automatico si se sube el justificante presentado, desbloqueado despues del tramite en programa gestion.
- Finalizado / Incidencia: decision final entre cerrar expediente o abrir una ruta de incidencia y subsanacion.

Cada cierre de hito debe escribir un evento en historial con fecha, usuario, estado anterior, estado nuevo y evidencia asociada.

## Datos clave de expediente

La cabecera del expediente debe mostrar como datos principales:

- Matricula.
- Tipo de tramite.

La tarjeta de informacion general debe evitar repetir matricula y tipo de tramite. En esa zona se priorizan:

- Fecha de inicio.
- Interesados con nombre, rol y datos de contacto basicos.

## Documentos

- Los documentos deben mostrarse como PDFs cuando proceda.
- La pantalla debe ofrecer controles visibles para subir, editar y borrar documentos.
- La accion de ver documentos puede abrir un checklist con documentos subidos y faltantes.
- Cuando falte un documento requerido, debe mostrarse un warning claro.

## Pantalla piloto

La primera pantalla de migracion React es:

- `/expedientes/:id`

Esta pantalla debe validar el modelo visual y funcional antes de extender patrones al resto de la aplicacion.

## Estrategia de migracion

- Mantener Spring Boot como backend.
- Crear endpoints JSON bajo `/api`.
- Migrar pantallas una a una desde Thymeleaf.
- Usar React, TypeScript y Vite para el nuevo frontend.
- Extraer componentes compartidos solo cuando existan patrones repetidos reales.
- Resolver de forma explicita la estrategia de autenticacion de la SPA antes de conectar flujos sensibles.

## Decisiones recientes

- La migracion se realizara en la rama `codex/react-frontend-migration`.
- Se prefiere Figma para explorar propuestas visuales antes de implementar cambios grandes en React.
- Paper Design se descarta por ahora.
- Las decisiones importantes de UX y UI deben quedar registradas en `PRODUCT.md` y `DESIGN.md`.
