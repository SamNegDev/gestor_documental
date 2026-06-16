package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoRequest;
import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoResponse;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/seguimiento-config")
@RequiredArgsConstructor
public class ConfiguracionSeguimientoApiController {
    private final ConfiguracionSeguimientoService configService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ConfiguracionSeguimientoResponse obtener(Authentication authentication) {
        return configService.obtenerResponse(currentUserService.requireAdmin(authentication));
    }

    @PutMapping
    public ConfiguracionSeguimientoResponse actualizar(@RequestBody ConfiguracionSeguimientoRequest request, Authentication authentication) {
        return configService.actualizar(request, currentUserService.requireAdmin(authentication));
    }
}
