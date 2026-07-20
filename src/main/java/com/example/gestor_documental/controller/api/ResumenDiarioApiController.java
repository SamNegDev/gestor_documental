package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.ResumenDiarioTramitesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/resumen-diario-tramites")
@RequiredArgsConstructor
public class ResumenDiarioApiController {

    private final ResumenDiarioTramitesService resumenDiarioTramitesService;
    private final CurrentUserService currentUserService;

    @PostMapping("/enviar-prueba")
    public ResumenDiarioResponse enviarPrueba(
            @RequestParam(defaultValue = "true") boolean incluirClientesSinCambios,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        ResumenDiarioTramitesService.ResultadoResumenDiario resultado =
                resumenDiarioTramitesService.enviarResumenDiarioManual(incluirClientesSinCambios);
        return new ResumenDiarioResponse(resultado.clientesEnviados(), resultado.cambiosIncluidos(), resultado.avisos());
    }

    @PostMapping("/clientes/{clienteId}/enviar")
    public ResumenDiarioResponse enviarCliente(
            @PathVariable Long clienteId,
            @RequestParam(defaultValue = "true") boolean incluirClienteSinCambios,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        ResumenDiarioTramitesService.ResultadoResumenDiario resultado =
                resumenDiarioTramitesService.enviarResumenDiarioManualCliente(clienteId, incluirClienteSinCambios);
        return new ResumenDiarioResponse(resultado.clientesEnviados(), resultado.cambiosIncluidos(), resultado.avisos());
    }

    @PostMapping("/incidencias/enviar-masivo")
    public ResumenDiarioResponse enviarIncidencias(
            @RequestParam(required = false) Long clienteId,
            Authentication authentication
    ) {
        var admin = currentUserService.requireAdmin(authentication);
        validarClienteEnvioMasivo(clienteId);
        ResumenDiarioTramitesService.ResultadoResumenDiario resultado =
                resumenDiarioTramitesService.enviarListadoIncidenciasManual(clienteId, admin);
        return new ResumenDiarioResponse(resultado.clientesEnviados(), resultado.cambiosIncluidos(), resultado.avisos());
    }

    @PostMapping("/incidencias/pendientes-notificar/enviar-masivo")
    public ResumenDiarioResponse enviarIncidenciasPendientesNotificar(
            @RequestParam(required = false) Long clienteId,
            Authentication authentication
    ) {
        var admin = currentUserService.requireAdmin(authentication);
        validarClienteEnvioMasivo(clienteId);
        ResumenDiarioTramitesService.ResultadoResumenDiario resultado =
                resumenDiarioTramitesService.enviarListadoIncidenciasPendientesNotificar(clienteId, admin);
        return new ResumenDiarioResponse(resultado.clientesEnviados(), resultado.cambiosIncluidos(), resultado.avisos());
    }

    @PostMapping("/incidencias/seleccion/preview")
    public PrevisualizacionAvisoConjuntoResponse previsualizarIncidenciasSeleccionadas(
            @RequestBody IncidenciasSeleccionadasRequest request,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        var preview = resumenDiarioTramitesService.previsualizarListadoIncidenciasSeleccionadas(
                request != null ? request.incidenciaIds() : List.of());
        return new PrevisualizacionAvisoConjuntoResponse(preview.destinatario(), preview.asunto(), preview.texto(),
                preview.html(), preview.incidencias(), preview.expedientes(), preview.envioReal());
    }
    @PostMapping("/incidencias/seleccion/enviar")
    public ResumenDiarioResponse enviarIncidenciasSeleccionadas(
            @RequestBody IncidenciasSeleccionadasRequest request,
            Authentication authentication
    ) {
        var admin = currentUserService.requireAdmin(authentication);
        ResumenDiarioTramitesService.ResultadoResumenDiario resultado =
                resumenDiarioTramitesService.enviarListadoIncidenciasSeleccionadas(
                        request != null ? request.incidenciaIds() : List.of(),
                        admin
                );
        return new ResumenDiarioResponse(resultado.clientesEnviados(), resultado.cambiosIncluidos(), resultado.avisos());
    }

    private void validarClienteEnvioMasivo(Long clienteId) {
        if (clienteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un cliente antes de enviar una notificacion masiva.");
        }
    }

    public record ResumenDiarioResponse(int clientesEnviados, int cambiosIncluidos, List<String> avisos) {
    }

    public record PrevisualizacionAvisoConjuntoResponse(String destinatario, String asunto, String texto, String html,
                                                        int incidencias, int expedientes, boolean envioReal) {
    }

    public record IncidenciasSeleccionadasRequest(List<Long> incidenciaIds) {
    }
}
