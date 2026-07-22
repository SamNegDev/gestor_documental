package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UsuarioServiceImpl service;

    @Test
    void administradorPuedeSeleccionarUnClienteOCambiarATodos() {
        Usuario admin = new Usuario("Admin", "Test", "admin@test.local", "secret", RolUsuario.ADMIN, true);
        admin.setId(1L);
        Cliente cliente = new Cliente();
        cliente.setId(20L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(clienteRepository.findById(20L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.save(admin)).thenReturn(admin);

        assertThat(service.seleccionarClienteActivo(1L, 20L).getCliente()).isSameAs(cliente);
        assertThat(service.seleccionarClienteActivo(1L, null).getCliente()).isNull();
    }

    @Test
    void usuarioClienteNoPuedeSeleccionarTodosLosClientes() {
        Usuario usuario = new Usuario("Cliente", "Test", "cliente@test.local", "secret", RolUsuario.CLIENTE, true);
        usuario.setId(2L);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.seleccionarClienteActivo(2L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
