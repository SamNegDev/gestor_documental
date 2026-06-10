package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.DashboardMetricsResponse;
import com.example.gestor_documental.dto.expediente.DashboardResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteListItemResponse;
import com.example.gestor_documental.dto.expediente.SolicitudListItemResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.UsuarioService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioService usuarioService;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;

    @GetMapping
    public DashboardResponse obtenerDashboard(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            return adminDashboard();
        }
        if (usuario.getCliente() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un cliente asociado");
        }
        return clienteDashboard(usuario);
    }

    private DashboardResponse adminDashboard() {
        long enTramite = expedienteService.contarPorEstado(EstadoExpediente.EN_TRAMITE)
                + expedienteService.contarPorEstado(EstadoExpediente.REVISANDO_INCIDENCIAS)
                + expedienteService.contarPorEstado(EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL)
                + expedienteService.contarPorEstado(EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA);
        long incidenciasExpedientes = expedienteService.contarPorEstado(EstadoExpediente.INCIDENCIA);
        long incidenciasSolicitudes = solicitudService.contarPorEstado(EstadoSolicitud.PENDIENTE_DOCUMENTACION);

        return DashboardResponse.builder()
                .scope("ADMIN")
                .metrics(DashboardMetricsResponse.builder()
                        .totalExpedientes(expedienteService.contarTodos())
                        .enTramite(enTramite)
                        .finalizados(expedienteService.contarPorEstado(EstadoExpediente.FINALIZADO))
                        .incidenciasExpedientes(incidenciasExpedientes)
                        .totalSolicitudes(solicitudService.contarTodos())
                        .pendienteRevision(solicitudService.contarPorEstado(EstadoSolicitud.PENDIENTE_REVISION))
                        .convertidas(solicitudService.contarPorEstado(EstadoSolicitud.CONVERTIDA))
                        .incidenciasSolicitudes(incidenciasSolicitudes)
                        .totalIncidencias(incidenciasExpedientes + incidenciasSolicitudes)
                        .build())
                .ultimosExpedientes(expedienteService.listarUltimos().stream().map(this::mapExpediente).toList())
                .ultimasSolicitudes(solicitudService.listarUltimas().stream().map(this::mapSolicitud).toList())
                .build();
    }

    private DashboardResponse clienteDashboard(Usuario usuario) {
        var cliente = usuario.getCliente();
        long enTramite = expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.EN_TRAMITE)
                + expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.REVISANDO_INCIDENCIAS)
                + expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL)
                + expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA);
        long incidenciasExpedientes = expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.INCIDENCIA);
        long incidenciasSolicitudes = solicitudService.contarPorClienteYEstado(cliente, EstadoSolicitud.PENDIENTE_DOCUMENTACION);

        return DashboardResponse.builder()
                .scope("CLIENTE")
                .metrics(DashboardMetricsResponse.builder()
                        .totalExpedientes(expedienteService.contarPorCliente(cliente))
                        .enTramite(enTramite)
                        .finalizados(expedienteService.contarPorClienteYEstado(cliente, EstadoExpediente.FINALIZADO))
                        .incidenciasExpedientes(incidenciasExpedientes)
                        .totalSolicitudes(solicitudService.contarPorCliente(cliente))
                        .pendienteRevision(solicitudService.contarPorClienteYEstado(cliente, EstadoSolicitud.PENDIENTE_REVISION))
                        .convertidas(solicitudService.contarPorClienteYEstado(cliente, EstadoSolicitud.CONVERTIDA))
                        .incidenciasSolicitudes(incidenciasSolicitudes)
                        .totalIncidencias(incidenciasExpedientes + incidenciasSolicitudes)
                        .build())
                .ultimosExpedientes(expedienteService.listarUltimosPorCliente(cliente).stream().map(this::mapExpediente).toList())
                .ultimasSolicitudes(solicitudService.listarUltimasPorCliente(cliente).stream().map(this::mapSolicitud).toList())
                .build();
    }

    private ExpedienteListItemResponse mapExpediente(Expediente expediente) {
        return ExpedienteListItemResponse.builder()
                .id(expediente.getId())
                .matricula(expediente.getMatricula())
                .tipoTramite(expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                        ? expediente.getTipoTramite().getNombre().name()
                        : null)
                .estado(expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name() : null)
                .fechaCreacion(formatDate(expediente.getFechaCreacion()))
                .fechaUltimaModificacion(formatDate(expediente.getFechaUltimaModificacion()))
                .cliente(mapCliente(expediente.getCliente()))
                .modificadoPor(mapUsuario(expediente.getModificadoPor()))
                .build();
    }

    private SolicitudListItemResponse mapSolicitud(Solicitud solicitud) {
        return SolicitudListItemResponse.builder()
                .id(solicitud.getId())
                .matricula(solicitud.getMatricula())
                .tipoTramite(solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getNombre() != null
                        ? solicitud.getTipoTramite().getNombre().name()
                        : null)
                .estado(solicitud.getEstadoSolicitud() != null ? solicitud.getEstadoSolicitud().name() : null)
                .fechaCreacion(formatDate(solicitud.getFechaCreacion()))
                .fechaUltimaModificacion(formatDate(solicitud.getFechaUltimaModificacion()))
                .cliente(mapCliente(solicitud.getCliente()))
                .modificadoPor(mapUsuario(solicitud.getModificadoPor()))
                .expedienteId(solicitud.getExpediente() != null ? solicitud.getExpediente().getId() : null)
                .build();
    }

    private ClienteResumenResponse mapCliente(com.example.gestor_documental.model.Cliente cliente) {
        if (cliente == null) {
            return null;
        }
        return ClienteResumenResponse.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .nif(cliente.getNif())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .build();
    }

    private UsuarioResumenResponse mapUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String completo = (nombre + " " + apellidos).trim();
        return UsuarioResumenResponse.builder()
                .id(usuario.getId())
                .nombreCompleto(!completo.isEmpty() ? completo : usuario.getEmail())
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .build();
    }

    private String formatDate(LocalDateTime fecha) {
        return fecha != null ? fecha.format(DATE_TIME_FORMATTER) : null;
    }
}
