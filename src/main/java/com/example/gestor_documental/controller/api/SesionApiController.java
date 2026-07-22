package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.util.ClienteBrandingUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SesionApiController {

    private final CurrentUserService currentUserService;
    private final ClienteService clienteService;

    @GetMapping
    public UsuarioResumenResponse obtenerSesion(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion no iniciada");
        }

        Usuario usuario = currentUserService.requireUser(authentication);
        return UsuarioResumenResponse.builder()
                .id(usuario.getId())
                .nombreCompleto(nombreCompleto(usuario))
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .cliente(mapCliente(usuario.getCliente()))
                .clientes((usuario.getRolUsuario() == RolUsuario.ADMIN
                        ? clienteService.listarTodos().stream()
                        : usuario.getClientesAutorizados().stream()).map(this::mapCliente).toList())
                .build();
    }

    @PutMapping("/cliente-activo")
    public UsuarioResumenResponse seleccionarClienteActivo(
            @RequestBody ClienteActivoRequest request,
            Authentication authentication
    ) {
        Usuario usuario = currentUserService.requireUser(authentication);
        if (request.clienteId() == null && usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un cliente");
        }
        currentUserService.seleccionarClienteActivo(usuario, request.clienteId());
        return obtenerSesion(authentication);
    }

    public record ClienteActivoRequest(Long clienteId) {
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
                .logoPrincipalUrl(ClienteBrandingUrls.logoUrl(cliente, TipoLogoCliente.PRINCIPAL))
                .logoCompactoUrl(ClienteBrandingUrls.logoUrl(cliente, TipoLogoCliente.COMPACTO))
                .build();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String completo = (nombre + " " + apellidos).trim();
        return completo.isEmpty() ? usuario.getEmail() : completo;
    }
}
