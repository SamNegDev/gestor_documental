package com.example.gestor_documental.security;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UsuarioService usuarioService;

    public Usuario requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccesoDenegadoException("Sesion no iniciada");
        }
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario == null) {
            throw new AccesoDenegadoException("Usuario no encontrado");
        }
        return usuario;
    }

    public Usuario requireAdmin(Authentication authentication) {
        Usuario usuario = requireUser(authentication);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede realizar esta operacion");
        }
        return usuario;
    }

    public Usuario seleccionarClienteActivo(Usuario usuario, Long clienteId) {
        return usuarioService.seleccionarClienteActivo(usuario.getId(), clienteId);
    }
}
