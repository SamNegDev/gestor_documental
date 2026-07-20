package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.AvisoIncidencia;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.TipoIncidenciaRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.CorreoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.WhatsappOutboundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidenciaSeguimientoServiceImplTest {

    @Mock IncidenciaRepository incidenciaRepository;
    @Mock HitoExpedienteRepository hitoExpedienteRepository;
    @Mock AvisoIncidenciaRepository avisoIncidenciaRepository;
    @Mock TipoIncidenciaRepository tipoIncidenciaRepository;
    @Mock MensajeRepository mensajeRepository;
    @Mock RequisitoDocumentalExpedienteRepository requisitoRepository;
    @Mock ExpedienteService expedienteService;
    @Mock SolicitudService solicitudService;
    @Mock TipoIncidenciaService tipoIncidenciaService;
    @Mock HistorialCambioService historialCambioService;
    @Mock MensajeService mensajeService;
    @Mock CorreoService correoService;
    @Mock WhatsappOutboundService whatsappOutboundService;
    @Mock ConfiguracionSeguimientoService configuracionSeguimientoService;

    @InjectMocks
    IncidenciaServiceImpl service;

    private Usuario admin;
    private Incidencia incidencia;
    private ConfiguracionSeguimiento config;

    @BeforeEach
    void setUp() {
        admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);
        Cliente cliente = new Cliente();
        cliente.setTelefono("+34600000000");
        cliente.setEmail("cliente@example.com");
        Expediente expediente = new Expediente();
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        expediente.setCliente(cliente);
        incidencia = new Incidencia();
        incidencia.setExpediente(expediente);
        config = new ConfiguracionSeguimiento();
    }

    @Test
    void registraElErrorDeWhatsappSinAvanzarElSeguimiento() {
        when(incidenciaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(incidencia));
        when(configuracionSeguimientoService.obtener()).thenReturn(config);
        when(whatsappOutboundService.enviarAvisoSeguimiento(anyString(), anyString()))
                .thenReturn(WhatsappOutboundService.ResultadoWhatsapp.error("Proveedor no disponible"));

        var result = service.notificarClienteWhatsapp(1L, "Mensaje", admin);

        assertFalse(result.exito());
        assertEquals(0, incidencia.getContadorAvisos());
        ArgumentCaptor<AvisoIncidencia> captor = ArgumentCaptor.forClass(AvisoIncidencia.class);
        verify(avisoIncidenciaRepository).save(captor.capture());
        assertEquals("ERROR", captor.getValue().getEstadoEnvio());
        assertEquals("Proveedor no disponible", captor.getValue().getErrorEnvio());
        verify(mensajeService, never()).añadirAExpediente(org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bloqueaElEnvioCuandoSeAlcanzaElMaximo() {
        incidencia.setContadorAvisos(config.getMaxAvisos());
        when(incidenciaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(incidencia));
        when(configuracionSeguimientoService.obtener()).thenReturn(config);

        assertThrows(OperacionInvalidaException.class,
                () -> service.notificarClienteWhatsapp(1L, "Mensaje", admin));
        verify(whatsappOutboundService, never()).enviarAvisoSeguimiento(anyString(), anyString());
    }

    @Test
    void noReactivaUnaIncidenciaResuelta() {
        incidencia.setResuelta(true);
        incidencia.setSeguimientoArchivado(true);
        when(incidenciaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(incidencia));

        assertThrows(OperacionInvalidaException.class, () -> service.reactivarSeguimiento(1L, admin));
        verify(incidenciaRepository, never()).save(incidencia);
    }
}