package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.service.ConfiguracionSeguimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvisosClienteProgramadosServiceTest {

    @Mock ClienteRepository clienteRepository;
    @Mock ResumenFinalizadosDiarioService finalizadosService;
    @Mock ResumenDiarioTramitesService incidenciasService;
    @Mock ConfiguracionSeguimientoService configuracionSeguimientoService;

    @InjectMocks
    AvisosClienteProgramadosService service;

    @BeforeEach
    void configurarLimites() {
        ReflectionTestUtils.setField(service, "zone", "Atlantic/Canary");
        ReflectionTestUtils.setField(service, "maxPerRun", 5);
        ReflectionTestUtils.setField(service, "maxPerDay", 20);
    }

    @Test
    void noConsultaNadaSiLaBanderaDeEntornoEstaDesactivada() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.procesar();

        verify(configuracionSeguimientoService, never()).obtener();
        verify(clienteRepository, never()).countByUltimoAvisoIncidencias(any());
    }

    @Test
    void noEnviaMientrasElModoSupervisadoEstaActivo() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ConfiguracionSeguimiento config = configuracionActiva();
        config.setModoSupervisado(true);
        when(configuracionSeguimientoService.obtener()).thenReturn(config);

        service.procesar();

        verify(clienteRepository, never()).countByUltimoAvisoIncidencias(any());
        verify(incidenciasService, never()).enviarListadoIncidenciasAutomaticoCliente(any());
        verify(finalizadosService, never()).enviarClienteDelDia(any());
    }

    @Test
    void reservaAntesDeEnviarYNoReintentaTrasUnFalloDelProveedor() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(configuracionSeguimientoService.obtener()).thenReturn(configuracionActiva());
        when(clienteRepository.countByUltimoAvisoIncidencias(any())).thenReturn(0L);
        when(clienteRepository.countByUltimoAvisoFinalizados(any())).thenReturn(0L);
        Cliente cliente = cliente(7L);
        when(clienteRepository.findPendientesAvisoIncidencias(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(cliente));
        when(clienteRepository.reservarAvisoIncidencias(eq(7L), any())).thenReturn(1);
        when(incidenciasService.enviarListadoIncidenciasAutomaticoCliente(7L))
                .thenThrow(new IllegalStateException("fallo posterior al envio"));
        when(clienteRepository.findPendientesAvisoFinalizados(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.procesar();

        verify(clienteRepository).reservarAvisoIncidencias(eq(7L), any(LocalDate.class));
        verify(incidenciasService).enviarListadoIncidenciasAutomaticoCliente(7L);
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void omiteElEnvioSiOtraEjecucionYaHaReservadoElCliente() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(configuracionSeguimientoService.obtener()).thenReturn(configuracionActiva());
        when(clienteRepository.countByUltimoAvisoIncidencias(any())).thenReturn(0L);
        when(clienteRepository.countByUltimoAvisoFinalizados(any())).thenReturn(0L);
        Cliente cliente = cliente(9L);
        when(clienteRepository.findPendientesAvisoIncidencias(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(cliente));
        when(clienteRepository.reservarAvisoIncidencias(eq(9L), any())).thenReturn(0);
        when(clienteRepository.findPendientesAvisoFinalizados(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.procesar();

        verify(incidenciasService, never()).enviarListadoIncidenciasAutomaticoCliente(any());
    }

    @Test
    void detieneElLoteAlAlcanzarElLimiteDiario() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(configuracionSeguimientoService.obtener()).thenReturn(configuracionActiva());
        when(clienteRepository.countByUltimoAvisoIncidencias(any())).thenReturn(12L);
        when(clienteRepository.countByUltimoAvisoFinalizados(any())).thenReturn(8L);

        service.procesar();

        verify(clienteRepository, never()).findPendientesAvisoIncidencias(any(), any(), any(Pageable.class));
        verify(clienteRepository, never()).findPendientesAvisoFinalizados(any(), any(), any(Pageable.class));
    }

    private ConfiguracionSeguimiento configuracionActiva() {
        ConfiguracionSeguimiento config = new ConfiguracionSeguimiento();
        config.setAutomatizacionActiva(true);
        config.setModoSupervisado(false);
        config.setCanalAutomatico("EMAIL");
        config.setDiasEnvio("TODOS");
        config.setHoraEnvio(0);
        config.setTamanioLote(50);
        return config;
    }

    private Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        return cliente;
    }
}
