package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.plantilla.DocumentoGeneradoResponse;
import com.example.gestor_documental.dto.plantilla.GenerarPlantillaRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewResponse;
import com.example.gestor_documental.dto.plantilla.PlantillasExpedienteResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.UsuarioService;
import com.example.gestor_documental.service.impl.PlantillaDocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expedientes/{expedienteId}/plantillas")
@RequiredArgsConstructor
public class PlantillaDocumentoApiController {

    private final PlantillaDocumentoService plantillaService;
    private final UsuarioService usuarioService;

    @GetMapping
    public PlantillasExpedienteResponse catalogo(@PathVariable Long expedienteId, Authentication authentication) {
        return plantillaService.catalogo(expedienteId, requireAdmin(authentication));
    }

    @PostMapping("/preview")
    public PlantillaPreviewResponse preview(@PathVariable Long expedienteId,
            @RequestBody PlantillaPreviewRequest request, Authentication authentication) {
        return plantillaService.preview(expedienteId, request, requireAdmin(authentication));
    }

    @PostMapping("/generar")
    public DocumentoGeneradoResponse generar(@PathVariable Long expedienteId,
            @RequestBody GenerarPlantillaRequest request, Authentication authentication) {
        return plantillaService.generar(expedienteId, request, requireAdmin(authentication));
    }

    private Usuario requireAdmin(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario == null || usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede gestionar plantillas documentales");
        }
        return usuario;
    }
}
