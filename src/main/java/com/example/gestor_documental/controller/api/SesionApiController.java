package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SesionApiController {

    private final UsuarioService usuarioService;

    @GetMapping
    public UsuarioResumenResponse obtenerSesion(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion no iniciada");
        }

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        return UsuarioResumenResponse.builder()
                .id(usuario.getId())
                .nombreCompleto(nombreCompleto(usuario))
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .build();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String completo = (nombre + " " + apellidos).trim();
        return completo.isEmpty() ? usuario.getEmail() : completo;
    }
}
