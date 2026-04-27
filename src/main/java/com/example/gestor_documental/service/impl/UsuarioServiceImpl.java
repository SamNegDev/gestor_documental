package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Override
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Override
    public void eliminarPorId(Long id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    public Usuario crearUsuario(Usuario usuario, Long clienteId, String rawPassword) {
        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
            usuario.setCliente(cliente);
        } else {
            usuario.setCliente(null);
        }

        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario nuevo.");
        } else {
            usuario.setPassword(passwordEncoder.encode(rawPassword.trim()));
        }

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario actualizarUsuario(Long id, Usuario datosNuevos, Long clienteId, String newRawPassword) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + id));

        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
            existente.setCliente(cliente);
        } else {
            existente.setCliente(null);
        }

        existente.setNombre(datosNuevos.getNombre());
        existente.setApellidos(datosNuevos.getApellidos());
        existente.setEmail(datosNuevos.getEmail());
        existente.setRolUsuario(datosNuevos.getRolUsuario());
        existente.setActivo(datosNuevos.isActivo());

        if (newRawPassword != null && !newRawPassword.trim().isEmpty()) {
            existente.setPassword(passwordEncoder.encode(newRawPassword.trim()));
        }

        return usuarioRepository.save(existente);
    }

    @Override
    public void eliminarUsuarioSeguro(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + id));

        if (usuario.isActivo()) {
            throw new IllegalStateException("El usuario debe estar inactivo para poder ser eliminado permanentemente.");
        }
        
        usuarioRepository.deleteById(id);
    }
}
