package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.registro.InteresadoUpdateRequest;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.InteresadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteresadoServiceImplTest {

    @Mock
    private InteresadoRepository interesadoRepository;
    @InjectMocks
    private InteresadoServiceImpl service;

    @Test
    void actualizarParticularRespetaElNombreEditadoYApartaUnaRazonSocialAntigua() {
        Interesado interesado = new Interesado("45551127H", "CLIENTE INCORRECTO");
        interesado.setId(580L);
        interesado.setRazonSocial("CLIENTE INCORRECTO");
        interesado.setTipoPersona(TipoPersona.PARTICULAR);
        when(interesadoRepository.findById(580L)).thenReturn(Optional.of(interesado));
        when(interesadoRepository.findByDni("45551127H")).thenReturn(Optional.of(interesado));
        when(interesadoRepository.save(interesado)).thenReturn(interesado);

        Interesado actualizado = service.actualizar(580L, request("Vanesa", "CLIENTE INCORRECTO", TipoPersona.PARTICULAR));

        assertEquals("VANESA", actualizado.getNombre());
        assertNull(actualizado.getRazonSocial());
    }

    @Test
    void actualizarEmpresaMantieneLaRazonSocialComoNombreVisible() {
        Interesado interesado = new Interesado("B38436556", "NOMBRE ANTERIOR");
        interesado.setId(555L);
        when(interesadoRepository.findById(555L)).thenReturn(Optional.of(interesado));
        when(interesadoRepository.findByDni("B38436556")).thenReturn(Optional.of(interesado));
        when(interesadoRepository.save(interesado)).thenReturn(interesado);

        Interesado actualizado = service.actualizar(555L, request("Nombre manual", "Empresa correcta SL", TipoPersona.EMPRESA));

        assertEquals("EMPRESA CORRECTA SL", actualizado.getNombre());
        assertEquals("EMPRESA CORRECTA SL", actualizado.getRazonSocial());
    }

    private InteresadoUpdateRequest request(String nombre, String razonSocial, TipoPersona tipoPersona) {
        return new InteresadoUpdateRequest(
                tipoPersona == TipoPersona.EMPRESA ? "B38436556" : "45551127H",
                nombre,
                null, null, null, razonSocial,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                tipoPersona
        );
    }
}