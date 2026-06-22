package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.ResumenDiarioTramitesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public record ResumenDiarioResponse(int clientesEnviados, int cambiosIncluidos, List<String> avisos) {
    }
}
