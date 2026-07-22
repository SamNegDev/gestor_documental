package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.expediente.HistorialExpedienteResponse;
import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.HistorialCambioConsultaRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ExpedienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HistorialExpedienteApiController {

    private static final List<String> ACCIONES_COMUNICACION = List.of(
            "AVISO INCIDENCIA", "AVISO PENDIENTE", "LISTADO INCIDENCIAS", "SEGUIMIENTO POSPUESTO");

    private final HistorialCambioConsultaRepository historialRepository;
    private final ExpedienteService expedienteService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/expedientes/{id}/historial")
    public PagedResponse<HistorialExpedienteResponse> listarAdmin(
            @PathVariable Long id,
            @RequestParam(required = false) CategoriaHistorial categoria,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio,
            Authentication authentication) {
        Usuario usuario = validarAcceso(id, authentication);
        return listar(id, categoria, pagina, tamanio, false);
    }

    @GetMapping("/api/cliente/expedientes/{id}/historial")
    public PagedResponse<HistorialExpedienteResponse> listarCliente(
            @PathVariable Long id,
            @RequestParam(required = false) CategoriaHistorial categoria,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio,
            Authentication authentication) {
        validarAcceso(id, authentication);
        return listar(id, categoria, pagina, tamanio, true);
    }

    private Usuario validarAcceso(Long expedienteId, Authentication authentication) {
        Usuario usuario = currentUserService.requireUser(authentication);
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        return usuario;
    }

    private PagedResponse<HistorialExpedienteResponse> listar(Long expedienteId, CategoriaHistorial categoria,
                                                               int pagina, int tamanio, boolean soloCliente) {
        PageRequest pageable = PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 50)),
                Sort.by(Sort.Order.desc("fechaCambio"), Sort.Order.desc("id")));
        Page<HistorialExpedienteResponse> resultado = historialRepository.buscar(
                expedienteId, categoria, soloCliente,
                List.of(AudienciaHistorial.CLIENTE, AudienciaHistorial.AMBOS),
                TipoActividadHistorial.COMUNICACION, ACCIONES_COMUNICACION, pageable).map(this::mapear);
        return PagedResponse.of(resultado);
    }

    private HistorialExpedienteResponse mapear(HistorialCambio cambio) {
        Usuario usuario = cambio.getUsuario();
        String nombre = usuario != null
                ? ((usuario.getNombre() != null ? usuario.getNombre() : "") + " "
                    + (usuario.getApellidos() != null ? usuario.getApellidos() : "")).trim()
                : null;
        return HistorialExpedienteResponse.builder()
                .id(cambio.getId()).accion(cambio.getAccion()).descripcion(cambio.getDescripcion())
                .fechaCambio(cambio.getFechaCambio() != null ? cambio.getFechaCambio().toString() : null)
                .usuario(nombre != null && !nombre.isBlank() ? nombre : null)
                .tipoActividad(cambio.getTipoActividad().name())
                .categoria(cambio.getCategoriaClasificada().name())
                .audiencia(cambio.getAudienciaClasificada().name())
                .build();
    }
}
