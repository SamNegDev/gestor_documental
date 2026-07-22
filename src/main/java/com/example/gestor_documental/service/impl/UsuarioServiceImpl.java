package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.service.UsuarioService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        return usuarioRepository.findWithClienteByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        normalizarUsuario(usuario);
        return usuarioRepository.save(usuario);
    }

    @Override
    public void eliminarPorId(Long id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Usuario crearUsuario(Usuario usuario, List<Long> clienteIds, Long clienteActivoId, String rawPassword) {
        asignarClientes(usuario, clienteIds, clienteActivoId);

        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario nuevo.");
        } else {
            usuario.setPassword(passwordEncoder.encode(rawPassword.trim()));
        }

        normalizarUsuario(usuario);
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, Usuario datosNuevos, List<Long> clienteIds, Long clienteActivoId, String newRawPassword) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + id));

        asignarClientes(existente, clienteIds, clienteActivoId);

        existente.setNombre(TextNormalizer.upperOrNull(datosNuevos.getNombre()));
        existente.setApellidos(TextNormalizer.upperOrNull(datosNuevos.getApellidos()));
        existente.setEmail(TextNormalizer.lowerOrNull(datosNuevos.getEmail()));
        existente.setRolUsuario(datosNuevos.getRolUsuario());
        existente.setActivo(datosNuevos.isActivo());

        if (newRawPassword != null && !newRawPassword.trim().isEmpty()) {
            existente.setPassword(passwordEncoder.encode(newRawPassword.trim()));
        }

        return usuarioRepository.save(existente);
    }

    @Override
    @Transactional
    public Usuario seleccionarClienteActivo(Long usuarioId, Long clienteId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (usuario.getRolUsuario() == com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            Cliente cliente = clienteId == null
                    ? null
                    : clienteRepository.findById(clienteId)
                            .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
            usuario.setCliente(cliente);
            return usuarioRepository.save(usuario);
        }
        if (clienteId == null) {
            throw new IllegalArgumentException("Selecciona un cliente activo");
        }

        Cliente cliente = usuario.getClientesAutorizados().stream()
                .filter(autorizado -> autorizado.getId().equals(clienteId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El cliente no esta autorizado para este usuario"));
        usuario.setCliente(cliente);
        return usuarioRepository.save(usuario);
    }

    private void asignarClientes(Usuario usuario, List<Long> clienteIds, Long clienteActivoId) {
        Set<Long> ids = clienteIds == null
                ? Set.of()
                : clienteIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Cliente> clientes = clienteRepository.findAllById(ids);
        if (clientes.size() != ids.size()) {
            throw new IllegalArgumentException("Alguno de los clientes seleccionados no existe");
        }
        usuario.getClientesAutorizados().clear();
        usuario.getClientesAutorizados().addAll(clientes);
        if (clienteActivoId == null) {
            usuario.setCliente(null);
            return;
        }
        Cliente activo = clientes.stream()
                .filter(cliente -> cliente.getId().equals(clienteActivoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El cliente activo debe estar entre los clientes asignados"));
        usuario.setCliente(activo);
    }

    private void normalizarUsuario(Usuario usuario) {
        usuario.setNombre(TextNormalizer.upperOrNull(usuario.getNombre()));
        usuario.setApellidos(TextNormalizer.upperOrNull(usuario.getApellidos()));
        usuario.setEmail(TextNormalizer.lowerOrNull(usuario.getEmail()));
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
