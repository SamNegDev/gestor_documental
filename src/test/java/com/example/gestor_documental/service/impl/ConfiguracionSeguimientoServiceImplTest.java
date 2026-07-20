package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoRequest;
import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ConfiguracionSeguimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionSeguimientoServiceImplTest {

    @Mock
    private ConfiguracionSeguimientoRepository repository;

    private ConfiguracionSeguimientoServiceImpl service;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        service = new ConfiguracionSeguimientoServiceImpl(repository);
        admin = new Usuario();
        admin.setRolUsuario(RolUsuario.ADMIN);
    }

    @Test
    void actualizaTodasLasReglasSeguras() {
        when(repository.findById(ConfiguracionSeguimiento.ID_UNICO)).thenReturn(Optional.of(new ConfiguracionSeguimiento()));
        when(repository.save(any(ConfiguracionSeguimiento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ConfiguracionSeguimientoRequest request = new ConfiguracionSeguimientoRequest(
                3, 5, 7, 14, 30, 4, 10,
                2, false, true, "laborables", 9, 75, "email");

        ConfiguracionSeguimientoResponse result = service.actualizar(request, admin);

        assertEquals(2, result.diasPrimerAviso());
        assertFalse(result.automatizacionActiva());
        assertTrue(result.modoSupervisado());
        assertEquals("LABORABLES", result.diasEnvio());
        assertEquals(9, result.horaEnvio());
        assertEquals(75, result.tamanioLote());
        assertEquals("EMAIL", result.canalAutomatico());
    }

    @Test
    void normalizaUnaConfiguracionCreadaAntesDeLosNuevosCampos() {
        ConfiguracionSeguimiento antigua = new ConfiguracionSeguimiento();
        antigua.setDiasEnvio(null);
        antigua.setCanalAutomatico(null);
        antigua.setTamanioLote(0);
        antigua.setHoraEnvio(0);
        antigua.setModoSupervisado(false);
        when(repository.findById(ConfiguracionSeguimiento.ID_UNICO)).thenReturn(Optional.of(antigua));
        when(repository.save(any(ConfiguracionSeguimiento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfiguracionSeguimiento normalizada = service.obtener();

        assertEquals(2, normalizada.getDiasPrimerAviso());
        assertEquals("LABORABLES", normalizada.getDiasEnvio());
        assertEquals(9, normalizada.getHoraEnvio());
        assertEquals(50, normalizada.getTamanioLote());
        assertEquals("EMAIL", normalizada.getCanalAutomatico());
        assertTrue(normalizada.isModoSupervisado());
    }
    @Test
    void rechazaHoraYLoteFueraDeRango() {
        ConfiguracionSeguimientoRequest request = new ConfiguracionSeguimientoRequest(
                3, 5, 7, 14, 30, 4, 10,
                2, false, true, "LABORABLES", 24, 0, "EMAIL");

        assertThrows(OperacionInvalidaException.class, () -> service.actualizar(request, admin));
    }

    @Test
    void rechazaCanalODiasDesconocidos() {
        ConfiguracionSeguimientoRequest request = new ConfiguracionSeguimientoRequest(
                3, 5, 7, 14, 30, 4, 10,
                2, false, true, "FESTIVOS", 9, 50, "SMS");

        assertThrows(OperacionInvalidaException.class, () -> service.actualizar(request, admin));
    }
}