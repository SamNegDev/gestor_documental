package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.seguimiento.SeguimientoIncidenciaResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaPreviewResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaRequest;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaResponse;
import com.example.gestor_documental.dto.seguimiento.PosponerSeguimientoRequest;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.model.AvisoIncidencia;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.util.MensajeAutomaticoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/seguimiento-clientes")
@RequiredArgsConstructor
public class SeguimientoClienteApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final IncidenciaRepository incidenciaRepository;
    private final AvisoIncidenciaRepository avisoRepository;
    private final MensajeRepository mensajeRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final IncidenciaService incidenciaService;
    private final CurrentUserService currentUserService;
    private final ConfiguracionSeguimientoService configuracionSeguimientoService;

    @GetMapping
    public PagedResponse<SeguimientoIncidenciaResponse> listar(@RequestParam(defaultValue = "PENDIENTES") String vista,
            @RequestParam(required = false) Long clienteId, @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) String accion,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamanio,
            Authentication authentication) {
        requireAdmin(authentication);
        if (!"PENDIENTES".equals(vista) && !"ARCHIVADAS".equals(vista)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vista de seguimiento no valida");
        }
        if (accion != null && !accion.isBlank() && !"TODAS".equals(accion) && !"RECORDATORIO_VENCIDO".equals(accion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de accion no valido");
        }
        boolean archivadas = "ARCHIVADAS".equals(vista);
        LocalDateTime ahora = LocalDateTime.now();
        Page<Incidencia> incidencias = incidenciaRepository.findPaginaSeguimiento(
                archivadas, clienteId, anio, "RECORDATORIO_VENCIDO".equals(accion), ahora,
                PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 100))));
        List<Long> incidenciaIds = incidencias.getContent().stream().map(Incidencia::getId).toList();
        List<Long> expedienteIds = incidencias.getContent().stream().map(item -> item.getExpediente().getId()).distinct().toList();
        Map<Long, List<AvisoIncidencia>> avisos = incidenciaIds.isEmpty() ? Map.of()
                : avisoRepository.findByIncidenciaIdInOrderByFechaEnvioAsc(incidenciaIds).stream()
                .collect(Collectors.groupingBy(item -> item.getIncidencia().getId()));
        Map<Long, List<RequisitoDocumentalExpediente>> requisitos = expedienteIds.isEmpty() ? Map.of()
                : requisitoRepository.findByExpedienteIdInOrderByIdAsc(expedienteIds).stream()
                .collect(Collectors.groupingBy(item -> item.getExpediente().getId()));
        Map<Long, List<Mensaje>> mensajes = expedienteIds.isEmpty() ? Map.of()
                : mensajeRepository.findByExpedienteIdInOrderByFechaCreacionAsc(expedienteIds).stream()
                .collect(Collectors.groupingBy(item -> item.getExpediente().getId()));
        int maxAvisos = configuracionSeguimientoService.obtener().getMaxAvisos();
        Page<SeguimientoIncidenciaResponse> respuesta = incidencias.map(incidencia -> map(
                incidencia,
                avisos.getOrDefault(incidencia.getId(), List.of()),
                requisitos.getOrDefault(incidencia.getExpediente().getId(), List.of()),
                mensajes.getOrDefault(incidencia.getExpediente().getId(), List.of()),
                maxAvisos,
                ahora));
        return PagedResponse.of(respuesta);
    }

    @GetMapping("/{id}/notificacion-preview")
    public NotificacionIncidenciaPreviewResponse preview(@PathVariable Long id, Authentication authentication) {
        return incidenciaService.previsualizarNotificacion(id, requireAdmin(authentication));
    }

    @GetMapping("/{id}/notificacion-whatsapp-preview")
    public NotificacionIncidenciaPreviewResponse previewWhatsapp(@PathVariable Long id, Authentication authentication) {
        return incidenciaService.previsualizarNotificacionWhatsapp(id, requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{id}/preparar-notificacion")
    public NotificacionIncidenciaPreviewResponse prepararNotificacion(@PathVariable Long id, Authentication authentication) {
        Incidencia incidencia = incidenciaService.prepararNotificacionExpediente(id, requireAdmin(authentication));
        return incidenciaService.previsualizarNotificacion(incidencia.getId(), requireAdmin(authentication));
    }

    @PostMapping("/{id}/notificar")
    public NotificacionIncidenciaResponse notificar(@PathVariable Long id, @RequestBody(required = false) NotificacionIncidenciaRequest body, Authentication authentication) {
        NotificacionIncidenciaResponse resultado = incidenciaService.notificarCliente(id, body != null ? body.asunto() : null, body != null ? body.mensaje() : null, requireAdmin(authentication));
        if (!resultado.exito()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, resultado.mensaje());
        return resultado;
    }

    @PostMapping("/{id}/notificar-whatsapp")
    public NotificacionIncidenciaResponse notificarWhatsapp(@PathVariable Long id, @RequestBody(required = false) NotificacionIncidenciaRequest body, Authentication authentication) {
        NotificacionIncidenciaResponse resultado = incidenciaService.notificarClienteWhatsapp(id, body != null ? body.mensaje() : null, requireAdmin(authentication));
        if (!resultado.exito()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, resultado.mensaje());
        return resultado;
    }
    @PostMapping("/{id}/posponer") public void posponer(@PathVariable Long id, @RequestBody PosponerSeguimientoRequest body, Authentication authentication) { incidenciaService.posponerSeguimiento(id, body != null ? body.proximoAviso() : null, requireAdmin(authentication)); }
    @PostMapping("/{id}/archivar") public void archivar(@PathVariable Long id, Authentication authentication) { incidenciaService.archivarSeguimiento(id, requireAdmin(authentication)); }
    @PostMapping("/{id}/reactivar") public void reactivar(@PathVariable Long id, Authentication authentication) { incidenciaService.reactivarSeguimiento(id, requireAdmin(authentication)); }

    private SeguimientoIncidenciaResponse map(Incidencia incidencia,
                                               List<AvisoIncidencia> avisos,
                                               List<RequisitoDocumentalExpediente> requisitos,
                                               List<Mensaje> mensajes,
                                               int maxAvisos,
                                               LocalDateTime ahora) {
        return SeguimientoIncidenciaResponse.builder().incidenciaId(incidencia.getId()).expedienteId(incidencia.getExpediente().getId())
                .matricula(incidencia.getExpediente().getMatricula()).clienteId(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getId() : null)
                .cliente(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getNombre() : null)
                .tipoIncidencia(tipoIncidencia(incidencia))
                .observaciones(observacionesSeguimiento(incidencia, requisitos, mensajes)).avisosEnviados(incidencia.getContadorAvisos())
                .maxAvisos(maxAvisos)
                .fechaPrimerAviso(avisos.isEmpty() ? null : format(avisos.get(0).getFechaEnvio())).fechaUltimoAviso(format(incidencia.getFechaUltimoAviso()))
                .proximoAviso(format(incidencia.getProximoAviso())).pendienteNotificacion(incidencia.getContadorAvisos() == 0 || incidencia.getProximoAviso() != null && !incidencia.getProximoAviso().isAfter(ahora))
                .archivada(incidencia.isSeguimientoArchivado()).fechaArchivo(format(incidencia.getFechaArchivoSeguimiento()))
                .anioExpediente(incidencia.getExpediente().getFechaCreacion() != null ? incidencia.getExpediente().getFechaCreacion().getYear() : 0).build();
    }

    private String observacionesSeguimiento(Incidencia incidencia,
                                             List<RequisitoDocumentalExpediente> requisitos,
                                             List<Mensaje> mensajes) {
        TipoIncidenciaEnum tipo = incidencia.getTipoIncidencia() != null ? incidencia.getTipoIncidencia().getNombre() : null;
        if (tipo == TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION) {
            String detalleRequisitos = requisitos.stream()
                    .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                    .map(this::descripcionRequisito)
                    .filter(valor -> valor != null && !valor.isBlank())
                    .distinct()
                    .collect(Collectors.joining(" - "));
            return !detalleRequisitos.isBlank() ? detalleRequisitos : observacionVisible(incidencia);
        }
        if (tipo == TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL) {
            String mensaje = ultimoMensajeAdmin(mensajes);
            if (mensaje != null) {
                return mensaje;
            }
            String observacion = observacionVisible(incidencia);
            return observacion != null ? observacion : "RESPONDER A LA INFORMACION SOLICITADA";
        }
        return observacionVisible(incidencia);
    }

    private String observacionVisible(Incidencia incidencia) {
        return MensajeAutomaticoUtils.observacionClienteActual(incidencia.getObservaciones());
    }

    private String descripcionRequisito(RequisitoDocumentalExpediente requisito) {
        if (requisito.getDescripcion() != null && !requisito.getDescripcion().isBlank()) {
            return requisito.getDescripcion().trim();
        }
        return requisito.getTipoDocumento() != null ? requisito.getTipoDocumento().name().replace('_', ' ') : null;
    }

    private String ultimoMensajeAdmin(List<Mensaje> mensajes) {
        for (int index = mensajes.size() - 1; index >= 0; index--) {
            Mensaje mensaje = mensajes.get(index);
            if (mensaje.getAutor() != null && mensaje.getAutor().getRolUsuario() == RolUsuario.ADMIN
                    && mensaje.getContenido() != null && !mensaje.getContenido().isBlank()
                    && !MensajeAutomaticoUtils.esMensajeAutomaticoSeguimiento(mensaje.getContenido())) {
                return mensaje.getContenido().trim();
            }
        }
        return null;
    }
    private String tipoIncidencia(Incidencia incidencia) {
        return incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null
                ? incidencia.getTipoIncidencia().getNombre().name()
                : null;
    }
    private Usuario requireAdmin(Authentication auth) { return currentUserService.requireAdmin(auth); }
    private String format(LocalDateTime fecha) { return fecha != null ? fecha.format(FORMAT) : null; }
}
