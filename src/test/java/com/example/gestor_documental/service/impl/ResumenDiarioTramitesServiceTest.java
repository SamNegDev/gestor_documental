package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.PreferenciaCanalCliente;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AvisoIncidenciaRepository;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import com.example.gestor_documental.service.CorreoService;
import com.example.gestor_documental.service.HistorialCambioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumenDiarioTramitesServiceTest {

    @Mock HistorialCambioRepository historialCambioRepository;
    @Mock IncidenciaRepository incidenciaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock AvisoIncidenciaRepository avisoIncidenciaRepository;
    @Mock CorreoService correoService;
    @Mock ConfiguracionSeguimientoService configuracionSeguimientoService;
    @Mock HistorialCambioService historialCambioService;

    @InjectMocks
    ResumenDiarioTramitesService service;

    @Test
    void bloqueaPrimerAvisoAntesDelPlazoConfigurado() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmail("cliente@example.com");
        cliente.setPreferenciaCanal(PreferenciaCanalCliente.EMAIL);
        Expediente expediente = new Expediente();
        expediente.setCliente(cliente);
        Incidencia incidencia = new Incidencia();
        incidencia.setId(1L);
        incidencia.setExpediente(expediente);
        incidencia.setFechaCreacion(LocalDateTime.now().minusDays(1));
        ConfiguracionSeguimiento config = new ConfiguracionSeguimiento();
        config.setDiasPrimerAviso(2);
        when(incidenciaRepository.findActivasResumenByIds(List.of(1L))).thenReturn(List.of(incidencia));
        when(configuracionSeguimientoService.obtener()).thenReturn(config);

        OperacionInvalidaException error = assertThrows(OperacionInvalidaException.class,
                () -> service.enviarListadoIncidenciasSeleccionadas(List.of(1L), new Usuario()));

        assertEquals("Alguna incidencia seleccionada aun no ha cumplido el plazo del primer aviso.", error.getMessage());
        verify(correoService, never()).enviarHtml(any(), any(), any(), any(), any(), any());
    }

    @Test
    void previsualizaElHtmlRealSinEnviarCorreo() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNombre("Maria");
        cliente.setEmail("cliente@example.com");
        cliente.setPreferenciaCanal(PreferenciaCanalCliente.EMAIL);
        Expediente expediente = new Expediente();
        expediente.setId(20L);
        expediente.setMatricula("1234 MBC");
        expediente.setCliente(cliente);
        Incidencia incidencia = new Incidencia();
        incidencia.setId(1L);
        incidencia.setExpediente(expediente);
        incidencia.setFechaCreacion(LocalDateTime.now().minusDays(3));
        incidencia.setTipoIncidencia(new TipoIncidencia(TipoIncidenciaEnum.RESERVA,
                "El vehículo posee una reserva de dominio activa.", true));
        incidencia.setObservaciones("INCIDENCIA ABIERTA DESDE EL CIERRE DEL EXPEDIENTE.");
        incidencia.setContadorAvisos(1);
        incidencia.setProximoAviso(LocalDateTime.now().minusHours(1));
        Incidencia incidenciaMayusculas = new Incidencia();
        incidenciaMayusculas.setId(2L);
        incidenciaMayusculas.setExpediente(expediente);
        incidenciaMayusculas.setFechaCreacion(LocalDateTime.now().minusDays(4));
        incidenciaMayusculas.setTipoIncidencia(new TipoIncidencia(TipoIncidenciaEnum.EMBARGO,
                "El vehículo tiene un embargo.", true));
        incidenciaMayusculas.setObservaciones("APORTAR DOCUMENTO DE LA DGT.");
        incidenciaMayusculas.setContadorAvisos(1);
        incidenciaMayusculas.setProximoAviso(LocalDateTime.now().minusHours(1));
        ConfiguracionSeguimiento config = new ConfiguracionSeguimiento();
        config.setDiasPrimerAviso(2);
        when(incidenciaRepository.findActivasResumenByIds(List.of(1L, 2L))).thenReturn(List.of(incidencia, incidenciaMayusculas));
        when(configuracionSeguimientoService.obtener()).thenReturn(config);

        var preview = service.previsualizarListadoIncidenciasSeleccionadas(List.of(1L, 2L));

        assertEquals("cliente@example.com", preview.destinatario());
        assertEquals(2, preview.incidencias());
        assertEquals(1, preview.expedientes());
        assertTrue(preview.html().contains("1234 MBC"));
        assertTrue(preview.html().contains("Acción requerida"));
        assertTrue(preview.html().contains("Reserva de dominio"));
        assertTrue(preview.html().contains("font-size:17px"));
        assertTrue(preview.html().contains("El vehículo tiene una reserva de dominio que impide la transmisión."));
        assertTrue(!preview.html().contains("reserva de dominio activa"));
        assertTrue(!preview.html().contains("INCIDENCIA ABIERTA DESDE EL CIERRE DEL EXPEDIENTE"));
        assertTrue(preview.html().contains("Aportar documento de la DGT."));
        assertTrue(!preview.html().contains("APORTAR DOCUMENTO DE LA DGT."));
        assertTrue(preview.html().contains("Recordatorio · Pendiente desde"));
        assertTrue(preview.html().indexOf("Reserva de dominio") < preview.html().indexOf("El vehículo tiene"));
        verify(correoService, never()).enviarHtml(any(), any(), any(), any(), any(), any());
    }
    @ParameterizedTest
    @EnumSource(value = PreferenciaCanalCliente.class, names = {"WHATSAPP", "SIN_AVISOS"})
    void bloqueaAvisoConjuntoPorEmailCuandoLaPreferenciaNoLoPermite(PreferenciaCanalCliente preferencia) {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmail("cliente@example.com");
        cliente.setPreferenciaCanal(preferencia);
        Expediente expediente = new Expediente();
        expediente.setCliente(cliente);
        Incidencia incidencia = new Incidencia();
        incidencia.setId(1L);
        incidencia.setExpediente(expediente);
        incidencia.setFechaCreacion(LocalDateTime.now().minusDays(3));
        ConfiguracionSeguimiento config = new ConfiguracionSeguimiento();
        when(incidenciaRepository.findActivasResumenByIds(List.of(1L))).thenReturn(List.of(incidencia));
        when(configuracionSeguimientoService.obtener()).thenReturn(config);

        assertThrows(OperacionInvalidaException.class,
                () -> service.enviarListadoIncidenciasSeleccionadas(List.of(1L), new Usuario()));

        verify(correoService, never()).enviarHtml(any(), any(), any(), any(), any(), any());
    }
}