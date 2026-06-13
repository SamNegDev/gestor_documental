package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.tarea.TareaResponse;
import com.example.gestor_documental.dto.tarea.TareasResumenResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.UsuarioService;
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
import java.util.Objects;

@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final UsuarioService usuarioService;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final MensajeRepository mensajeRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;

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
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            incidenciaRepository.findAllWithDetails().stream()
                    .filter(i -> !i.isResuelta() && !i.isSeguimientoArchivado() && i.getExpediente() != null)
                    .filter(i -> i.getExpediente().getEstadoExpediente() != EstadoExpediente.FINALIZADO && i.getExpediente().getEstadoExpediente() != EstadoExpediente.RECHAZADO)
                    .filter(i -> i.getContadorAvisos() == 0 || i.getProximoAviso() != null && !i.getProximoAviso().isAfter(LocalDateTime.now()))
                    .forEach(i -> tareas.add(TareaResponse.builder().id("INC-" + i.getId() + "-SEGUIMIENTO").tipo(i.getContadorAvisos() >= 5 ? "INCIDENCIA_PENDIENTE_ARCHIVAR" : "INCIDENCIA_PENDIENTE_NOTIFICAR").ambito("GESTION")
                            .prioridad("ALTA").titulo(i.getContadorAvisos() >= 5 ? "Seguimiento pendiente de archivar" : i.getContadorAvisos() == 0 ? "Incidencia pendiente de notificar" : "Recordatorio de incidencia vencido")
                            .detalle(i.getContadorAvisos() >= 5 ? "Se ha completado el ciclo de avisos sin respuesta." : i.getContadorAvisos() == 0 ? "Debe enviarse la primera notificacion al cliente." : "Debe renovarse la notificacion al cliente.")
                            .contexto(contextoIncidencia(i))
                            .entidad("INCIDENCIA").entidadId(i.getId()).matricula(i.getExpediente().getMatricula())
                            .cliente(i.getExpediente().getCliente() != null ? i.getExpediente().getCliente().getNombre() : null)
                            .fechaReferencia(format(i.getProximoAviso() != null ? i.getProximoAviso() : i.getFechaCreacion()))
                            .diasPendiente(dias(i.getProximoAviso() != null ? i.getProximoAviso() : i.getFechaCreacion()))
                            .enlace("/expedientes/" + i.getExpediente().getId()).build()));
        }
        List<Solicitud> solicitudes = usuario.getRolUsuario() == RolUsuario.ADMIN
                ? solicitudRepository.findAllOrderByFechaReferenciaDesc()
                : usuario.getCliente() != null ? solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(usuario.getCliente().getId()) : List.of();
        solicitudes.stream()
                .filter(solicitud -> solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_REVISION
                        || solicitud.getEstadoSolicitud() == EstadoSolicitud.REVISANDO_INCIDENCIAS
                        || solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_DOCUMENTACION)
                .filter(solicitud -> usuario.getRolUsuario() == RolUsuario.ADMIN
                        ? solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE_DOCUMENTACION
                        : solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_DOCUMENTACION)
                .forEach(solicitud -> tareas.add(tareaSolicitud(solicitud, usuario)));

        List<Expediente> expedientes = usuario.getRolUsuario() == RolUsuario.ADMIN
                ? expedienteRepository.findAllOrderByFechaReferenciaDesc()
                : usuario.getCliente() != null ? expedienteRepository.findByClienteIdOrderByFechaReferenciaDesc(usuario.getCliente().getId()) : List.of();
        for (Expediente expediente : expedientes) {
            LocalDateTime fecha = fechaReferencia(expediente);
            if (expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS
                    || expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
                tareas.add(tareaExpediente(expediente, "APORTACION_PENDIENTE_REVISION", "ALTA",
                        "Aportacion pendiente de revision", "El cliente ha aportado informacion o documentacion.",
                        contextoAportacion(expediente)));
            }
            if (usuario.getRolUsuario() == RolUsuario.CLIENTE && expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA) {
                tareas.add(tareaExpediente(expediente, "INCIDENCIA_PENDIENTE_CLIENTE", usuario.getRolUsuario() == RolUsuario.ADMIN ? "MEDIA" : "ALTA",
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "Incidencia pendiente del cliente" : "Incidencia pendiente de respuesta",
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "El cliente todavia no ha aportado la subsanacion." : "Debes responder o aportar la subsanacion solicitada.",
                        contextoIncidenciasExpediente(expediente.getId()),
                        usuario.getRolUsuario() == RolUsuario.ADMIN ? "SEGUIMIENTO" : "CLIENTE"));
            }
            if (usuario.getRolUsuario() == RolUsuario.CLIENTE && expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) tareas.add(tareaExpediente(expediente, "DOCUMENTACION_PENDIENTE_CLIENTE", "ALTA", "Documentacion pendiente", "Debes aportar la documentacion solicitada.", contextoDocumentacion(expediente.getId()), "CLIENTE"));
            if (usuario.getRolUsuario() == RolUsuario.CLIENTE && expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) tareas.add(tareaExpediente(expediente, "INFORMACION_PENDIENTE_CLIENTE", "ALTA", "Informacion pendiente", "Debes responder a la informacion solicitada.", ultimoMensaje(expediente.getId(), RolUsuario.ADMIN), "CLIENTE"));
            if (usuario.getRolUsuario() == RolUsuario.ADMIN && expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
                List<Documento> docs = documentoRepository.findByExpedienteId(expediente.getId());
                boolean dgt = docs.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE
                        || documento.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT);
                boolean modelo = docs.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
                if (!dgt || !modelo) {
                    String faltan = !dgt && !modelo ? "DGT y Modelo 620" : !dgt ? "justificante DGT" : "Modelo 620";
                    tareas.add(tareaExpediente(expediente, "JUSTIFICANTE_FINAL_PENDIENTE", "MEDIA",
                            "Justificante final pendiente", "Falta adjuntar " + faltan + ".",
                            "Expediente finalizado con justificantes pendientes. Falta adjuntar " + faltan + " para dejar el cierre documental completo."));
                }
            } else if (usuario.getRolUsuario() == RolUsuario.ADMIN && fecha != null && fecha.isBefore(LocalDateTime.now().minusDays(7))
                    && expediente.getEstadoExpediente() != EstadoExpediente.RECHAZADO
                    && expediente.getEstadoExpediente() != EstadoExpediente.INCIDENCIA
                    && expediente.getEstadoExpediente() != EstadoExpediente.PENDIENTE_DOCUMENTACION
                    && expediente.getEstadoExpediente() != EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
                tareas.add(tareaExpediente(expediente, "EXPEDIENTE_ESTANCADO", "MEDIA",
                        "Expediente sin actividad", "Lleva mas de siete dias sin modificaciones.",
                        contextoExpedienteEstancado(fecha)));
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
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario == null) throw new AccesoDenegadoException("Usuario no encontrado");
        return usuario;
    }
    private LocalDateTime fechaReferencia(Expediente expediente) { return expediente.getFechaUltimaModificacion() != null ? expediente.getFechaUltimaModificacion() : expediente.getFechaCreacion(); }
    private String format(LocalDateTime fecha) { return fecha != null ? fecha.format(FORMAT) : null; }
    private long dias(LocalDateTime fecha) { return fecha == null ? 0 : Math.max(0, Duration.between(fecha, LocalDateTime.now()).toDays()); }
    private int ordenPrioridad(String prioridad) { return "ALTA".equals(prioridad) ? 0 : "MEDIA".equals(prioridad) ? 1 : 2; }
}
