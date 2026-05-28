# DESIGN.md

Fecha de actualizacion: 2026-05-20

## Direccion de diseno

Gestoria Portal debe sentirse como una herramienta profesional de trabajo: clara, ordenada, moderna y densa sin saturar. La estetica base es product UI sobria, no landing, no dashboard decorativo.

Escena de uso: un gestor administrativo trabaja durante el dia en escritorio, alternando expedientes, documentos y comunicaciones. Necesita saber rapido que fase esta activa, que falta y que puede hacer a continuacion.

## Flujo de decision visual

- Antes de redisenos grandes en React, primero se preparan propuestas en Figma.
- El usuario decide direccion, mezcla de variantes o ajustes antes de codificar.
- React se modifica despues de validar la propuesta visual.
- Las decisiones acordadas se documentan aqui para mantener continuidad.

Archivo Figma de exploracion:

- https://www.figma.com/design/zOsWk14N4ETf9ZDOFGzhdQ

## Arquitectura visual de expediente

La pantalla de detalle de expediente debe organizarse alrededor del proceso de tramitacion.

La direccion visual debe acercarse al detalle base de Thymeleaf: cabecera con matricula como ancla visual, estado visible, tarjetas sobrias de informacion general y documentos, y menos apariencia de dashboard.

Orden de prioridad:

1. Identidad del expediente: titulo, estado actual con jerarquia alta y datos clave (matricula, tipo de tramite, fecha de inicio e interesados).
2. Siguiente accion o paso recomendado.
3. Hitos de la fase actual de tramitacion.
4. Documentos y requisitos asociados a la fase.
6. Informacion secundaria en tabs.
7. Historial y trazabilidad bajo demanda.

## Fases de tramitacion

El componente principal debe comunicar avance lineal:

1. Expediente creado
2. Tramitando expediente
3. Tramitado / Incidencias

Reglas de UX:

- Las fases deben orientar, pero no ser el foco principal de la pantalla.
- La informacion del expediente y los documentos deben tener mas peso visual que el componente de fases.
- Las fases completadas deben leerse como progreso ya resuelto.
- Las fases futuras deben aparecer disponibles pero sin competir con la fase actual.
- Las incidencias no deben ocultarse, pero deben mostrarse como estado operativo dentro de la fase final o junto al proceso cuando bloqueen el avance.
- Al crear un expediente, se asume que la solicitud ya fue revisada y que la documentacion base era correcta.
- Los botones de avance deben indicar el destino concreto, por ejemplo `Pasar a Tramitado / Incidencias`, no una accion generica como `Avanzar fase`.
- Si existe una incidencia, el flujo puede ampliar sus pasos: incidencia, subsanacion, revision y vuelta a tramitado.

Detalle recomendado para la fase operativa:

1. Solicitud revisada y convertida en expediente
2. Documentacion completa
3. Tramite subido en programa gestion
4. Modelo 620 presentado
5. Finalizado / Incidencia

Reglas de checks de fase:

- Los checks de fase son hitos secuenciales de proceso, no tareas libres.
- Cada hito tiene un tipo de validacion: automatico, manual, manual con documento o decision final.
- Los hitos automaticos se completan cuando el sistema tiene evidencia suficiente, por ejemplo documentos requeridos subidos y validados.
- Los hitos manuales deben pedir evidencia minima: fecha, referencia, nota o documento asociado.
- Los hitos posteriores deben mostrarse bloqueados o pendientes hasta que el hito anterior permita avanzar.
- El Modelo 620 no cuenta como documentacion base faltante: se tramita posteriormente, despues de registrar el tramite en el programa de gestion.
- Cada check completado debe generar trazabilidad: fecha, usuario, accion, estado anterior, nuevo estado y evidencia asociada.
- Si aparece una incidencia, el flujo debe abrir una ruta alternativa de subsanacion antes de volver a un punto final.

## Documentos

Reglas:

- Usar iconografia de PDF para documentos PDF.
- La tabla de documentos debe tener controles visibles de subir, editar y borrar.
- `Ver documentos` puede abrir un modal con checklist de documentos subidos y faltantes.
- Los documentos faltantes deben mostrar warning visual y texto de estado.
- La documentacion no debe quedar escondida dentro de tabs si forma parte del avance del tramite.

## Tabs

Las tabs se usan para informacion secundaria o menos urgente.

Tabs recomendadas para expediente cuando la documentacion ya tiene bloque propio:

- Datos del expediente.
- Comunicaciones.
- Historial.

Reglas:

- El historial queda oculto por defecto y disponible cuando haga falta revisarlo.
- Las tabs no deben ser necesarias para entender en que fase esta el tramite.
- La informacion critica de avance no debe vivir exclusivamente dentro de una tab secundaria.

## Layout

- Mantener una estructura de aplicacion con menu lateral y barra superior.
- El menu lateral y la topbar deben estar bien alineados, sin competir con el contenido principal.
- La zona principal debe tener jerarquia clara: cabecera, fase, trabajo actual, informacion secundaria.
- La cabecera del expediente debe ser compacta: estado actual, titulo y datos clave del expediente; evitar textos explicativos largos o parrillas de datos secundarios.
- Datos clave visibles en cabecera: matricula y tipo de tramite. No repetir esos datos en la tarjeta inferior.
- La matricula debe tener presencia visual propia, similar a la placa del detalle Thymeleaf.
- La tarjeta de informacion general cercana a la cabecera debe priorizar fecha de inicio e interesados, con una vista de interesados mas rica que un texto plano.
- El siguiente paso debe aparecer antes de los hitos de fase para que la accion inmediata sea lo primero tras identificar el expediente.
- El bloque de documentos se ubica debajo del siguiente paso y de los hitos de fase para que primero se lea la accion concreta, despues el contexto del proceso, y despues las evidencias asociadas.
- Evitar tarjetas dentro de tarjetas.
- Usar paneles solo cuando agrupen informacion funcional real.
- La densidad es positiva si ayuda a escanear y comparar, pero cada bloque debe tener aire suficiente.

## Componentes y stack UI

Stack actual del frontend React:

- React.
- TypeScript.
- Vite.
- React Router.
- TanStack Query.
- Radix UI para primitivas accesibles.
- Lucide React para iconos.
- `clsx` para composicion de clases.
- CSS propio con tokens y clases de aplicacion.

Radix ya se usa para:

- Tabs.
- Dropdown menu.
- Tooltip.
- Separator.

Reglas de componentes:

- Usar Radix para primitivas interactivas cuando aporte accesibilidad o comportamiento probado.
- Usar iconos de Lucide en botones de accion, navegacion y utilidades.
- Mantener un vocabulario visual consistente para botones, badges, tabs, menus y estados.
- Cada componente interactivo debe contemplar default, hover, focus, active, disabled y loading cuando aplique.
- Si se incorpora Tailwind mas adelante, debe mapearse a estos criterios y no sustituirlos por estilos improvisados.

## Color y tono

- Estrategia restringida con paleta corporativa: azul turquesa como color principal y amarillo corporativo como acento operativo.
- Colores aproximados del logo: azul `#38A8D8` / `#38A8E0`, amarillo `#F8C010`, grafito `#404040`, gris secundario `#888888`.
- El azul turquesa se reserva para accion primaria, seleccion actual, navegacion activa, documentos PDF y estado informativo. Para botones se usa una variante mas oscura por contraste.
- El amarillo corporativo se reserva para advertencias, documentos faltantes, incidencias, rutas alternativas y llamadas de atencion.
- Los fondos deben mantenerse claros y neutros para que la paleta corporativa no sature la herramienta.
- Los estados semanticos deben diferenciarse con claridad: success, warning, error, info, neutral.
- Evitar paletas dominadas por morado, azul oscuro generico, beige excesivo o gradientes decorativos.
- Evitar texto con gradiente, glassmorphism decorativo y adornos sin funcion.

## Tipografia

- Usar una sans de producto consistente.
- Jerarquia por tamano, peso y espaciado, no por decoracion.
- Evitar fuentes display en labels, botones, tablas o datos.
- No usar escalado fluido de tipografia para la UI operativa.

## Decision log

2026-05-19:

- Se acuerda que el expediente debe funcionar como fase lineal de proceso.
- El flujo base sera `Expediente creado -> Tramitando expediente -> Tramitado / Incidencias`.
- El checklist se reformula como apoyo a la fase actual, no como estructura principal.
- Las tabs quedan para informacion menos relevante o secundaria.
- El historial debe estar disponible, pero oculto por defecto.
- Figma sera el espacio de decision antes de implementar redisenos grandes en React.
- Se ajusta la prioridad: ficha del expediente y documentos por encima del componente de fases.
- El estado `Expediente creado` implica solicitud revisada y documentacion base correcta.
- Las incidencias pueden abrir una ruta adicional con subsanacion, revision y retorno a tramitado.
- Se incorporan controles de documentos: subir, editar y borrar.
- `Ver documentos` abrira un modal de checklist documental con warning en faltantes.
- El detalle operativo de fase queda definido como: solicitud revisada, documentacion completa, tramite subido en programa gestion, Modelo 620 presentado, finalizado/incidencia.
- Los checks de fase se definen como hitos secuenciales con validacion automatica o manual con evidencia, y cada cierre debe escribir trazabilidad en historial.
- Se compacta la cabecera del expediente y el orden principal queda: siguiente paso, hitos de fase, documentos.
- Se ajusta la paleta corporativa al logo real: azul `#38A8D8/#38A8E0` para estructura y acciones, amarillo `#F8C010` para avisos e incidencias, grafito para texto.
