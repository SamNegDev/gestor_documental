package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import java.util.List;
import org.junit.jupiter.api.Test;

class SolicitudServicePermisoTest {

    @Test
    void aislaSolicitudesSegunElClienteActivo() {
        SolicitudServiceImpl service = mock(SolicitudServiceImpl.class, CALLS_REAL_METHODS);
        Cliente clienteA = new Cliente();
        clienteA.setId(10L);
        Cliente clienteB = new Cliente();
        clienteB.setId(20L);
        Usuario usuario = new Usuario("Cliente", "Multiple", "cliente@test.local", "secret", RolUsuario.CLIENTE, true);
        usuario.getClientesAutorizados().addAll(List.of(clienteA, clienteB));
        usuario.setCliente(clienteA);
        Solicitud solicitudA = new Solicitud();
        solicitudA.setCliente(clienteA);
        Solicitud solicitudB = new Solicitud();
        solicitudB.setCliente(clienteB);

        assertThat(service.tienePermisoSolicitud(solicitudA, usuario)).isTrue();
        assertThat(service.tienePermisoSolicitud(solicitudB, usuario)).isFalse();

        usuario.setCliente(clienteB);

        assertThat(service.tienePermisoSolicitud(solicitudA, usuario)).isFalse();
        assertThat(service.tienePermisoSolicitud(solicitudB, usuario)).isTrue();
    }
}
