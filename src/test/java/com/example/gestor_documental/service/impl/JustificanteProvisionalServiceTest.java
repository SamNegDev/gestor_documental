package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JustificanteProvisionalServiceTest {

    @Test
    void clienteAjenoNoPuedeAcceder() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        JustificanteProvisionalRepository justificantes = mock(JustificanteProvisionalRepository.class);
        JustificanteProvisionalService service = new JustificanteProvisionalService(solicitudes, justificantes);
        Cliente propietario = cliente(1L), ajeno = cliente(2L);
        Solicitud solicitud = new Solicitud();
        ReflectionTestUtils.setField(solicitud, "id", 10L);
        solicitud.setCliente(propietario);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(solicitud));
        Usuario usuario = new Usuario();
        usuario.setRolUsuario(RolUsuario.CLIENTE);
        usuario.setCliente(ajeno);

        assertThrows(AccesoDenegadoException.class, () -> service.obtener(10L, usuario));
        assertThrows(AccesoDenegadoException.class, () -> service.solicitar(10L, usuario));
        verifyNoInteractions(justificantes);
    }

    @Test
    void permiteSolicitarJustificanteParaBatecom() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        JustificanteProvisionalRepository justificantes = mock(JustificanteProvisionalRepository.class);
        JustificanteProvisionalService service = new JustificanteProvisionalService(solicitudes, justificantes);
        Cliente propietario = cliente(1L);
        Solicitud solicitud = new Solicitud();
        ReflectionTestUtils.setField(solicitud, "id", 10L);
        solicitud.setCliente(propietario);
        solicitud.setTipoTramite(new TipoTramite(TipoTramiteEnum.BATECOM, "BATECOM", true));
        Usuario usuario = new Usuario();
        usuario.setRolUsuario(RolUsuario.CLIENTE);
        usuario.setCliente(propietario);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(solicitud));
        when(justificantes.findBySolicitudId(10L)).thenReturn(Optional.empty());
        when(justificantes.save(any(JustificanteProvisional.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.solicitar(10L, usuario);

        assertEquals(EstadoJustificanteProvisional.SOLICITADO, resultado.estado());
        verify(justificantes).save(any(JustificanteProvisional.class));
    }

    private Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setNif("N" + id);
        cliente.setNombre("C" + id);
        cliente.setEmail(id + "@x.es");
        return cliente;
    }
}