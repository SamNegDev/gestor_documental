package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.tarea.TareaResponse;
import com.example.gestor_documental.dto.tarea.TareasResumenResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final MensajeRepository mensajeRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final WhatsappAdjuntoRepository whatsappAdjuntoRepository;
    private final WhatsappWebhookEventoRepository whatsappWebhookEventoRepository;
    private final CurrentUserService currentUserService;
    private final ConfiguracionSeguimientoService configuracionSeguimientoService;

    @GetMapping
    public PagedResponse<TareaResponse> listar(@RequestParam(required = false) String tipo,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String ambito,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanio,
            Authentication authentication) {
        Usuario usuario = usuario(authentication);
        List<TareaResponse> tareas = calcularTareas(usuario).stream()
                .filter(tarea -> ambito == null || ambito.isBlank() || ambito.equals(tarea.getAmbito()))
                .filter(tarea -> tipo == null || tipo.isBlank() || tipo.equals(tarea.getTipo()))
                .filter(tarea -> prioridad == null || prioridad.isBlank() || prioridad.equals(tarea.getPrioridad()))
                .sorted(Comparator.comparingInt((TareaResponse tarea) -> ordenPrioridad(tarea.getPrioridad()))
                        .thenComparing(TareaResponse::getDiasPendiente, Comparator.reverseOrder()))
                .toList();
        return PagedResponse.of(tareas, pagina, tamanio);
    }

    @GetMapping("/resumen")
    public TareasResumenResponse resumen(Authentication authentication) {
        Usuario usuario = usuario(authentication);
        String ambito = usuario.getRolUsuario() == RolUsuario.ADMIN ? "GESTION" : "CLIENTE";
        List<TareaResponse> tareas = calcularTareas(usuario).stream().filter(tarea -> ambito.equals(tarea.getAmbito())).toList();
        return TareasResumenResponse.builder().total(tareas.size())
                .urgentes(tareas.stream().filter(tarea -> "ALTA".equals(tarea.getPrioridad())).count())
                .estancados(tareas.stream().filter(tarea -> "EXPEDIENTE_ESTANCADO".equals(tarea.getTipo())).count())
                .build();
    }

    private List<TareaResponse> calcularTareas(Usuario usuario) {
        List<TareaResponse> tareas = new ArrayList<>();
        Long clienteId = usuario.getRolUsuario() == RolUsuario.ADMIN || usuario.getCliente() == null
                ? null
                : usuario.getCliente().getId();
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            List<Incidencia> incidenciasPendientes = incidenciaRepository.findSeguimientoPendiente(LocalDateTime.now());
            incidenciasPendientes.forEach(i -> tareas.add(tareaSeguimientoIncidencia(i)));
            whatsappWebhookEventoRepository.findByEstadoWithExpediente(EstadoWhatsappEvento.PENDIENTE)
                    .forEach(evento -> tareas.add(tareaWhatsapp(evento)));
            whatsappWebhookEventoRepository.findByEstadoWithClienteWithoutExpediente(EstadoWhatsappEvento.PENDIENTE)
                    .forEach(evento -> tareas.add(tareaWhatsappSinExpediente(evento)));
            whatsappWebhookEventoRepository.findByEstadoWithoutCliente(EstadoWhatsappEvento.PENDIENTE)
                    .forEach(evento -> tareas.add(tareaWhatsappSinCliente(evento)));
            whatsappAdjuntoRepository.findByEstadoForTareas(EstadoWhatsappAdjunto.PENDIENTE_CLASIFICAR)
                    .forEach(adjunto -> tareas.add(tareaWhatsappAdjunto(adjunto)));
            Set<Long> expedientesConIncidenciaPendiente = incidenciasPendientes.stream()
                    .map(Incidencia::getExpediente)
                    .filter(Objects::nonNull)
                    .map(Expediente::getId)
                    .collect(Collectors.toSet());
            expedienteRepository.findTareasPorEstados(null, List.of(
                            EstadoExpediente.PENDIENTE_DOCUMENTACION,
                            EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL
                    ))
                    .stream()
                    .filter(expediente -> !expedientesConIncidenciaPendiente.contains(expediente.getId()))
                    .filter(expediente -> !tieneIncidenciaActiva(expediente.getId()))
                    .forEach(expediente -> tareas.add(tareaAvisoPendienteExpediente(expediente)));
        }

        List<EstadoSolicitud> estadosSolicitud = usuario.getRolUsuario() == RolUsuario.ADMIN
                ? List.of(EstadoSolicitud.PENDIENTE_REVISION, EstadoSolicitud.REVISANDO_INCIDENCIAS)
                : List.of(EstadoSolicitud.PENDIENTE_DOCUMENTACION);
        solicitudRepository.findTareasPendientes(clienteId, estadosSolicitud)
                .forEach(solicitud -> tareas.add(tareaSolicitud(solicitud, usuario)));

        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            expedienteRepository.findTareasPorEstados(null, List.of(
                            EstadoExpediente.REVISANDO_INCIDENCIAS,
                            EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA
                    ))
                    .forEach(expediente -> tareas.add(tareaExpediente(expediente, "APORTACION_PENDIENTE_REVISION", "ALTA",
                            "Aportacion pendiente de revision", "El cliente ha aportado informacion o documentacion.",
                            contextoAportacion(expediente))));

            tareas.addAll(tareasJustificantesFinales());
            int diasEstancado = configuracionSeguimientoService.obtener().getDiasExpedienteEstancado();
            expedienteRepository.findEstancados(LocalDateTime.now().minusDays(diasEstancado), List.of(
                            EstadoExpediente.RECHAZADO,
                            EstadoExpediente.INCIDENCIA,
                            EstadoExpediente.PENDIENTE_DOCUMENTACION,
                            EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL,
                            EstadoExpediente.FINALIZADO
                    ))
                    .forEach(expediente -> tareas.add(tareaExpediente(expediente, "EXPEDIENTE_ESTANCADO", "MEDIA",
                            "Expediente sin actividad", "Lleva mas de " + diasEstancado + " dias sin modificaciones.",
                            contextoExpedienteEstancado(fechaReferencia(expediente)))));
        } else if (clienteId != null) {
            List<Expediente> expedientes = expedienteRepository.findTareasPorEstados(clienteId, List.of(
                    EstadoExpediente.REVISANDO_INCIDENCIAS,
                    EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA,
                    EstadoExpediente.INCIDENCIA,
                    EstadoExpediente.PENDIENTE_DOCUMENTACION,
                    EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL
            ));
            for (Expediente expediente : expedientes) {
                if (expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS
                        || expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
                tareas.add(tareaExpediente(expediente, "APORTACION_PENDIENTE_REVISION", "ALTA",
                        "Aportacion pendiente de revision", "El cliente ha aportado informacion o documentacion.",
                        contextoAportacion(expediente)));
                }
                if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA) {
                tareas.add(tareaExpediente(expediente, "INCIDENCIA_PENDIENTE_CLIENTE", usuario.getRolUsuario() == RolUsuario.ADMIN ? "MEDIA" : "ALTA",
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "Incidencia pendiente del cliente" : "Incidencia pendiente de respuesta",
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "El cliente todavia no ha aportado la subsanacion." : "Debes responder o aportar la subsanacion solicitada.",
                        contextoIncidenciasExpediente(expediente.getId()),
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "SEGUIMIENTO" : "CLIENTE"));
                }
                if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) tareas.add(tareaExpediente(expediente, "DOCUMENTACION_PENDIENTE_CLIENTE", "ALTA", "Documentacion pendiente", "Debes aportar la documentacion solicitada.", contextoDocumentacion(expediente.getId()), "CLIENTE"));
                if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) tareas.add(tareaExpediente(expediente, "INFORMACION_PENDIENTE_CLIENTE", "ALTA", "Informacion pendiente", "Debes responder a la informacion solicitada.", ultimoMensaje(expediente.getId(), RolUsuario.ADMIN), "CLIENTE"));
            }
        }
        return tareas;
    }

    private TareaResponse tareaSeguimientoIncidencia(Incidencia incidencia) {
        LocalDateTime fecha = incidencia.getProximoAviso() != null ? incidencia.getProximoAviso() : incidencia.getFechaCreacion();
        int maxAvisos = configuracionSeguimientoService.obtener().getMaxAvisos();
        return TareaResponse.builder().id("INC-" + incidencia.getId() + "-SEGUIMIENTO").tipo(incidencia.getContadorAvisos() >= maxAvisos ? "INCIDENCIA_PENDIENTE_ARCHIVAR" : "INCIDENCIA_PENDIENTE_NOTIFICAR").ambito("GESTION")
                .prioridad("ALTA").titulo(incidencia.getContadorAvisos() >= maxAvisos ? "Seguimiento pendiente de archivar" : incidencia.getContadorAvisos() == 0 ? "Solicitud al cliente pendiente de aviso" : "Recordatorio de solicitud vencido")
                .detalle(incidencia.getContadorAvisos() >= maxAvisos ? "Se ha completado el ciclo de avisos sin respuesta." : incidencia.getContadorAvisos() == 0 ? "Debe enviarse la primera notificacion al cliente antes de pasarla a seguimiento." : "Debe renovarse la notificacion al cliente.")
                .contexto(contextoIncidencia(incidencia))
                .entidad("INCIDENCIA").entidadId(incidencia.getId()).matricula(incidencia.getExpediente().getMatricula())
                .cliente(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getNombre() : null)
                .fechaReferencia(format(fecha)).diasPendiente(dias(fecha))
                .enlace("/expedientes/" + incidencia.getExpediente().getId()).build();
    }

    private TareaResponse tareaAvisoPendienteExpediente(Expediente expediente) {
        boolean documentacion = expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION;
        String contexto = documentacion ? contextoDocumentacion(expediente.getId()) : ultimoMensaje(expediente.getId(), RolUsuario.ADMIN);
        return tareaExpediente(expediente, "INCIDENCIA_PENDIENTE_NOTIFICAR", "ALTA",
                documentacion ? "Documentacion pendiente de aviso" : "Informacion pendiente de aviso",
                "Este expediente ya esta pendiente del cliente. Abre el expediente para enviar o regularizar el aviso.",
                contexto);
    }

    private TareaResponse tareaWhatsapp(WhatsappWebhookEvento evento) {
        Expediente expediente = evento.getExpediente();
        LocalDateTime fecha = evento.getFechaRecepcion();
        String contacto = evento.getNombrePerfil() != null && !evento.getNombrePerfil().isBlank()
                ? evento.getNombrePerfil()
                : evento.getTelefono();
        String tipo = tipoTareaWhatsapp(evento);
        return TareaResponse.builder()
                .id("WSP-" + evento.getId() + "-REVISION")
                .tipo(tipo)
                .ambito("GESTION")
                .prioridad("ALTA")
                .titulo(tituloTareaWhatsapp(tipo))
                .detalle(detalleTareaWhatsapp(tipo))
                .contexto(limitar((contacto != null ? contacto + ": " : "") + (evento.getTexto() != null ? evento.getTexto() : "Mensaje sin texto visible.")))
                .entidad("WHATSAPP")
                .entidadId(evento.getId())
                .matricula(expediente.getMatricula())
                .cliente(expediente.getCliente() != null ? expediente.getCliente().getNombre() : null)
                .fechaReferencia(format(fecha))
                .diasPendiente(dias(fecha))
                .enlace("/expedientes/" + expediente.getId())
                .build();
    }

    private TareaResponse tareaWhatsappAdjunto(WhatsappAdjunto adjunto) {
        LocalDateTime fecha = adjunto.getFechaRecepcion();
        String contexto = "Archivo: " + (adjunto.getNombreArchivoOriginal() != null ? adjunto.getNombreArchivoOriginal() : "Adjunto sin nombre")
                + (adjunto.getMimeType() != null ? " - " + adjunto.getMimeType() : "")
                + (adjunto.getErrorDescarga() != null ? " - Error: " + adjunto.getErrorDescarga() : "");
        return TareaResponse.builder()
                .id("WSP-ADJ-" + adjunto.getId() + "-CLASIFICAR")
                .tipo("WHATSAPP_ADJUNTO_CLASIFICAR")
                .ambito("GESTION")
                .prioridad("ALTA")
                .titulo("Adjunto WhatsApp pendiente")
                .detalle("Clasifica el archivo recibido y vinculalo al expediente o cliente correcto.")
                .contexto(limitar(contexto))
                .entidad("WHATSAPP_ADJUNTO")
                .entidadId(adjunto.getId())
                .matricula(adjunto.getExpediente() != null ? adjunto.getExpediente().getMatricula() : null)
                .cliente(adjunto.getCliente() != null ? adjunto.getCliente().getNombre() : "Sin asociar")
                .fechaReferencia(format(fecha))
                .diasPendiente(dias(fecha))
                .enlace("/admin/whatsapp")
                .build();
    }

    private TareaResponse tareaWhatsappSinExpediente(WhatsappWebhookEvento evento) {
        LocalDateTime fecha = evento.getFechaRecepcion();
        String contacto = evento.getNombrePerfil() != null && !evento.getNombrePerfil().isBlank()
                ? evento.getNombrePerfil()
                : evento.getTelefono();
        String tipo = tipoTareaWhatsapp(evento);
        return TareaResponse.builder()
                .id("WSP-" + evento.getId() + "-REVISION")
                .tipo(tipo)
                .ambito("GESTION")
                .prioridad("ALTA")
                .titulo(tituloTareaWhatsapp(tipo))
                .detalle("Hay un mensaje de WhatsApp de un cliente identificado, pero sin expediente asociado.")
                .contexto(limitar((contacto != null ? contacto + ": " : "") + (evento.getTexto() != null ? evento.getTexto() : "Mensaje sin texto visible.")))
                .entidad("WHATSAPP")
                .entidadId(evento.getId())
                .matricula(null)
                .cliente(evento.getCliente() != null ? evento.getCliente().getNombre() : null)
                .fechaReferencia(format(fecha))
                .diasPendiente(dias(fecha))
                .enlace("/admin/whatsapp")
                .build();
    }

    private TareaResponse tareaWhatsappSinCliente(WhatsappWebhookEvento evento) {
        LocalDateTime fecha = evento.getFechaRecepcion();
        String contacto = evento.getNombrePerfil() != null && !evento.getNombrePerfil().isBlank()
                ? evento.getNombrePerfil()
                : evento.getTelefono();
        return TareaResponse.builder()
                .id("WSP-" + evento.getId() + "-ASOCIAR")
                .tipo("WHATSAPP_PENDIENTE_ASOCIAR")
                .ambito("GESTION")
                .prioridad("ALTA")
                .titulo("WhatsApp sin cliente asociado")
                .detalle("Hay que identificar el telefono y asociarlo a un cliente antes de poder automatizar acciones.")
                .contexto(limitar((contacto != null ? contacto + ": " : "") + (evento.getTexto() != null ? evento.getTexto() : "Mensaje sin texto visible.")))
                .entidad("WHATSAPP")
                .entidadId(evento.getId())
                .matricula(null)
                .cliente("Sin asociar")
                .fechaReferencia(format(fecha))
                .diasPendiente(dias(fecha))
                .enlace("/admin/whatsapp")
                .build();
    }

    private String tipoTareaWhatsapp(WhatsappWebhookEvento evento) {
        if ("gestapp_ya_lo_envie".equals(evento.getAccionCodigo())) {
            return "WHATSAPP_APORTACION_INDICADA";
        }
        if ("gestapp_contactar".equals(evento.getAccionCodigo())) {
            return "WHATSAPP_CONTACTO_SOLICITADO";
        }
        if ("gestapp_estado_tramite".equals(evento.getAccionCodigo()) || "gestapp_menu_expedientes".equals(evento.getAccionCodigo())) {
            return "WHATSAPP_ESTADO_SOLICITADO";
        }
        return "WHATSAPP_PENDIENTE_REVISION";
    }

    private String tituloTareaWhatsapp(String tipo) {
        if ("WHATSAPP_APORTACION_INDICADA".equals(tipo)) return "Cliente indica aportacion enviada";
        if ("WHATSAPP_CONTACTO_SOLICITADO".equals(tipo)) return "Cliente solicita contacto";
        if ("WHATSAPP_ESTADO_SOLICITADO".equals(tipo)) return "Cliente solicita estado del tramite";
        return "Respuesta WhatsApp pendiente";
    }

    private String detalleTareaWhatsapp(String tipo) {
        if ("WHATSAPP_APORTACION_INDICADA".equals(tipo)) return "El cliente pulso 'Ya lo envie'. Revisa si la documentacion o respuesta ha llegado.";
        if ("WHATSAPP_CONTACTO_SOLICITADO".equals(tipo)) return "El cliente ha pedido que la gestoria contacte con el.";
        if ("WHATSAPP_ESTADO_SOLICITADO".equals(tipo)) return "El cliente ha pedido una revision o actualizacion del estado de su tramite.";
        return "Hay un mensaje de WhatsApp asociado al expediente pendiente de revisar.";
    }

    private boolean tieneIncidenciaActiva(Long expedienteId) {
        return !incidenciaRepository.findByExpedienteIdAndResueltaFalse(expedienteId).isEmpty();
    }

    private List<TareaResponse> tareasJustificantesFinales() {
        List<Expediente> finalizados = expedienteRepository.findFinalizadosParaRevisionDocumental();
        if (finalizados.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Documento>> documentosPorExpediente = documentoRepository.findJustificantesFinalesByExpedienteIds(
                        finalizados.stream().map(Expediente::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(documento -> documento.getExpediente().getId()));
        List<TareaResponse> tareas = new ArrayList<>();
        for (Expediente expediente : finalizados) {
            List<Documento> docs = documentosPorExpediente.getOrDefault(expediente.getId(), List.of());
            boolean dgt = docs.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE
                    || documento.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT);
            boolean modelo = docs.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
            if (!dgt || !modelo) {
                String faltan = !dgt && !modelo ? "DGT y Modelo 620" : !dgt ? "justificante DGT" : "Modelo 620";
                tareas.add(tareaExpediente(expediente, "JUSTIFICANTE_FINAL_PENDIENTE", "MEDIA",
                        "Justificante final pendiente", "Falta adjuntar " + faltan + ".",
                        "Expediente finalizado con justificantes pendientes. Falta adjuntar " + faltan + " para dejar el cierre documental completo."));
            }
        }
        return tareas;
    }

    private TareaResponse tareaSolicitud(Solicitud solicitud, Usuario usuario) {
        LocalDateTime fecha = solicitud.getFechaUltimaModificacion() != null ? solicitud.getFechaUltimaModificacion() : solicitud.getFechaCreacion();
        boolean revision = solicitud.getEstadoSolicitud() == EstadoSolicitud.REVISANDO_INCIDENCIAS;
        boolean cliente = usuario.getRolUsuario() == RolUsuario.CLIENTE;
        return TareaResponse.builder().id("SOL-" + solicitud.getId() + "-" + solicitud.getEstadoSolicitud())
                .ambito(cliente ? "CLIENTE" : "GESTION")
                .tipo(revision ? "APORTACION_PENDIENTE_REVISION" : "SOLICITUD_PENDIENTE_REVISION")
                .prioridad(cliente || revision ? "ALTA" : "MEDIA").titulo(cliente ? "Documentacion pendiente" : revision ? "Subsanacion pendiente de revision" : "Solicitud pendiente de revision")
                .detalle(cliente ? "Debes aportar la documentacion solicitada." : revision ? "El cliente ha aportado una subsanacion." : "La solicitud todavia no ha sido revisada.")
                .contexto(contextoSolicitud(solicitud, revision))
                .entidad("SOLICITUD").entidadId(solicitud.getId()).matricula(solicitud.getMatricula())
                .cliente(solicitud.getCliente() != null ? solicitud.getCliente().getNombre() : null)
                .fechaReferencia(format(fecha)).diasPendiente(dias(fecha)).enlace("/solicitudes/" + solicitud.getId()).build();
    }

    private TareaResponse tareaExpediente(Expediente expediente, String tipo, String prioridad, String titulo, String detalle) {
        return tareaExpediente(expediente, tipo, prioridad, titulo, detalle, null, "GESTION");
    }

    private TareaResponse tareaExpediente(Expediente expediente, String tipo, String prioridad, String titulo, String detalle, String contexto) {
        return tareaExpediente(expediente, tipo, prioridad, titulo, detalle, contexto, "GESTION");
    }

    private TareaResponse tareaExpediente(Expediente expediente, String tipo, String prioridad, String titulo, String detalle, String contexto, String ambito) {
        LocalDateTime fecha = fechaReferencia(expediente);
        return TareaResponse.builder().id("EXP-" + expediente.getId() + "-" + tipo).tipo(tipo).ambito(ambito).prioridad(prioridad)
                .titulo(titulo).detalle(detalle).contexto(limitar(contexto)).entidad("EXPEDIENTE").entidadId(expediente.getId())
                .matricula(expediente.getMatricula()).cliente(expediente.getCliente() != null ? expediente.getCliente().getNombre() : null)
                .fechaReferencia(format(fecha)).diasPendiente(dias(fecha)).enlace("CLIENTE".equals(ambito) ? "/cliente/expedientes/" + expediente.getId() : "/expedientes/" + expediente.getId()).build();
    }

    private String contextoSolicitud(Solicitud solicitud, boolean revision) {
        String incidencias = incidenciaRepository.findBySolicitudIdAndResueltaFalse(solicitud.getId()).stream()
                .map(this::contextoIncidencia)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" · "));
        if (!incidencias.isBlank()) return limitar(incidencias);
        if (revision) return ultimoMensajeSolicitud(solicitud.getId(), RolUsuario.CLIENTE);
        return limitar(solicitud.getObservaciones());
    }

    private String contextoIncidenciasExpediente(Long expedienteId) {
        return limitar(incidenciaRepository.findByExpedienteIdAndResueltaFalse(expedienteId).stream()
                .map(this::contextoIncidencia)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" · ")));
    }

    private String contextoIncidencia(Incidencia incidencia) {
        String tipo = incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null
                ? incidencia.getTipoIncidencia().getNombre().name().replace('_', ' ')
                : "INCIDENCIA";
        String observaciones = limpiar(incidencia.getObservaciones());
        return limitar(observaciones != null ? tipo + ": " + observaciones : tipo);
    }

    private String contextoDocumentacion(Long expedienteId) {
        return limitar(requisitoRepository.findByExpedienteIdOrderByIdAsc(expedienteId).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(this::descripcionRequisito)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" · ")));
    }

    private String descripcionRequisito(RequisitoDocumentalExpediente requisito) {
        String descripcion = limpiar(requisito.getDescripcion());
        if (descripcion == null && requisito.getTipoDocumento() != null) {
            descripcion = requisito.getTipoDocumento().name().replace('_', ' ');
        }
        return descripcion;
    }

    private String contextoAportacion(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
            return ultimoMensaje(expediente.getId(), RolUsuario.CLIENTE);
        }
        String incidencia = contextoIncidenciasExpediente(expediente.getId());
        return incidencia != null ? incidencia : contextoDocumentacion(expediente.getId());
    }

    private String contextoExpedienteEstancado(LocalDateTime fecha) {
        long dias = dias(fecha);
        String fechaTexto = format(fecha);
        String referencia = fechaTexto != null ? "Ultima modificacion: " + fechaTexto + ". " : "";
        return limitar(referencia + "Sin cambios desde hace " + dias + " dias. Revisar si puede avanzar de fase o si requiere contacto con el cliente.");
    }

    private String ultimoMensaje(Long expedienteId, RolUsuario rol) {
        return ultimoMensaje(mensajeRepository.findByExpedienteIdOrderByFechaCreacionAsc(expedienteId), rol);
    }

    private String ultimoMensajeSolicitud(Long solicitudId, RolUsuario rol) {
        return ultimoMensaje(mensajeRepository.findBySolicitudIdOrderByFechaCreacionAsc(solicitudId), rol);
    }

    private String ultimoMensaje(List<Mensaje> mensajes, RolUsuario rol) {
        for (int index = mensajes.size() - 1; index >= 0; index--) {
            Mensaje mensaje = mensajes.get(index);
            if (mensaje.getAutor() != null && mensaje.getAutor().getRolUsuario() == rol) {
                return limitar(mensaje.getContenido());
            }
        }
        return null;
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().replaceAll("\\s+", " ");
    }

    private String limitar(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null || limpio.length() <= 360) return limpio;
        return limpio.substring(0, 357) + "...";
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }
    private LocalDateTime fechaReferencia(Expediente expediente) { return expediente.getFechaUltimaModificacion() != null ? expediente.getFechaUltimaModificacion() : expediente.getFechaCreacion(); }
    private String format(LocalDateTime fecha) { return fecha != null ? fecha.format(FORMAT) : null; }
    private long dias(LocalDateTime fecha) { return fecha == null ? 0 : Math.max(0, Duration.between(fecha, LocalDateTime.now()).toDays()); }
    private int ordenPrioridad(String prioridad) { return "ALTA".equals(prioridad) ? 0 : "MEDIA".equals(prioridad) ? 1 : 2; }
}
