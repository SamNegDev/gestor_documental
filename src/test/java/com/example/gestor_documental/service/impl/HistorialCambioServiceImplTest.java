package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HistorialCambioServiceImplTest {

    @Mock
    private HistorialCambioRepository historialCambioRepository;
    @Mock
    private ExpedienteRepository expedienteRepository;
    @Mock
    private SolicitudRepository solicitudRepository;
    @InjectMocks
    private HistorialCambioServiceImpl service;

    @Test
    void registrarComunicacionConservaLaFechaDeModificacionDelExpediente() {
        Expediente expediente = new Expediente();
        Usuario usuario = new Usuario();
        LocalDateTime fechaOriginal = LocalDateTime.of(2026, 7, 20, 9, 30);
        expediente.setFechaUltimaModificacion(fechaOriginal);

        service.registrarComunicacionExpediente(
                expediente,
                usuario,
                "AVISO INCIDENCIA",
                "Aviso enviado al cliente."
        );

        ArgumentCaptor<HistorialCambio> captor = ArgumentCaptor.forClass(HistorialCambio.class);
        verify(historialCambioRepository).save(captor.capture());
        verify(expedienteRepository, never()).save(expediente);

        HistorialCambio historial = captor.getValue();
        assertEquals(TipoActividadHistorial.COMUNICACION, historial.getTipoActividad());
        assertEquals("AVISO INCIDENCIA", historial.getAccion());
        assertSame(expediente, historial.getExpediente());
        assertSame(usuario, historial.getUsuario());
        assertEquals(fechaOriginal, expediente.getFechaUltimaModificacion());
    }
}
