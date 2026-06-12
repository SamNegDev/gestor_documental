package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.seguimiento.SeguimientoIncidenciaResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaPreviewResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaRequest;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.AvisoIncidencia;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/seguimiento-clientes")
@RequiredArgsConstructor
public class SeguimientoClienteApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final IncidenciaRepository incidenciaRepository;
    private final AvisoIncidenciaRepository avisoRepository;
    private final IncidenciaService incidenciaService;
    private final UsuarioService usuarioService;

    @GetMapping
    public PagedResponse<SeguimientoIncidenciaResponse> listar(@RequestParam(defaultValue = "PENDIENTES") String vista,
            @RequestParam(required = false) Long clienteId, @RequestParam(required = false) Integer anio,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamanio,
            Authentication authentication) {
        requireAdmin(authentication);
        boolean archivadas = "ARCHIVADAS".equals(vista);
        List<SeguimientoIncidenciaResponse> items = incidenciaRepository.findAll().stream()
                .filter(incidencia -> !incidencia.isResuelta() && incidencia.getExpediente() != null)
                .filter(incidencia -> incidencia.getExpediente().getEstadoExpediente() != EstadoExpediente.FINALIZADO
                        && incidencia.getExpediente().getEstadoExpediente() != EstadoExpediente.RECHAZADO)
                .filter(incidencia -> incidencia.isSeguimientoArchivado() == archivadas)
                .filter(incidencia -> archivadas || incidencia.getContadorAvisos() > 0
                        && incidencia.getProximoAviso() != null && incidencia.getProximoAviso().isAfter(LocalDateTime.now()))
                .filter(incidencia -> clienteId == null || incidencia.getExpediente().getCliente() != null && clienteId.equals(incidencia.getExpediente().getCliente().getId()))
                .filter(incidencia -> anio == null || incidencia.getExpediente().getFechaCreacion() != null && incidencia.getExpediente().getFechaCreacion().getYear() == anio)
                .map(this::map)
                .sorted(Comparator.comparing(SeguimientoIncidenciaResponse::getProximoAviso, Comparator.nullsFirst(String::compareTo)))
                .toList();
        return PagedResponse.of(items, pagina, tamanio);
    }

    @GetMapping("/{id}/notificacion-preview")
    public NotificacionIncidenciaPreviewResponse preview(@PathVariable Long id, Authentication authentication) {
        return incidenciaService.previsualizarNotificacion(id, requireAdmin(authentication));
    }

    @PostMapping("/{id}/notificar")
    public NotificacionIncidenciaResponse notificar(@PathVariable Long id, @RequestBody(required = false) NotificacionIncidenciaRequest body, Authentication authentication) {
        NotificacionIncidenciaResponse resultado = incidenciaService.notificarCliente(id, body != null ? body.asunto() : null, body != null ? body.mensaje() : null, requireAdmin(authentication));
        if (!resultado.exito()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, resultado.mensaje());
        return resultado;
    }
    @PostMapping("/{id}/archivar") public void archivar(@PathVariable Long id, Authentication authentication) { incidenciaService.archivarSeguimiento(id, requireAdmin(authentication)); }
    @PostMapping("/{id}/reactivar") public void reactivar(@PathVariable Long id, Authentication authentication) { incidenciaService.reactivarSeguimiento(id, requireAdmin(authentication)); }

    private SeguimientoIncidenciaResponse map(Incidencia incidencia) {
        List<AvisoIncidencia> avisos = avisoRepository.findByIncidenciaIdOrderByFechaEnvioAsc(incidencia.getId());
        return SeguimientoIncidenciaResponse.builder().incidenciaId(incidencia.getId()).expedienteId(incidencia.getExpediente().getId())
                .matricula(incidencia.getExpediente().getMatricula()).clienteId(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getId() : null)
                .cliente(incidencia.getExpediente().getCliente() != null ? incidencia.getExpediente().getCliente().getNombre() : null)
                .tipoIncidencia(incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null ? incidencia.getTipoIncidencia().getNombre().name() : null)
                .observaciones(incidencia.getObservaciones()).avisosEnviados(incidencia.getContadorAvisos())
                .fechaPrimerAviso(avisos.isEmpty() ? null : format(avisos.get(0).getFechaEnvio())).fechaUltimoAviso(format(incidencia.getFechaUltimoAviso()))
                .proximoAviso(format(incidencia.getProximoAviso())).pendienteNotificacion(incidencia.getContadorAvisos() == 0 || incidencia.getProximoAviso() != null && !incidencia.getProximoAviso().isAfter(LocalDateTime.now()))
                .archivada(incidencia.isSeguimientoArchivado()).fechaArchivo(format(incidencia.getFechaArchivoSeguimiento()))
                .anioExpediente(incidencia.getExpediente().getFechaCreacion() != null ? incidencia.getExpediente().getFechaCreacion().getYear() : 0).build();
    }
    private Usuario requireAdmin(Authentication auth) { Usuario u = usuarioService.buscarPorEmail(auth.getName()); if (u == null || u.getRolUsuario() != RolUsuario.ADMIN) throw new AccesoDenegadoException("Solo el administrador puede consultar el seguimiento"); return u; }
    private String format(LocalDateTime fecha) { return fecha != null ? fecha.format(FORMAT) : null; }
}
