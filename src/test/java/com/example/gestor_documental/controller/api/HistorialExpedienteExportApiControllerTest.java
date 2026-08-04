package com.example.gestor_documental.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialExpedienteExportService;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class HistorialExpedienteExportApiControllerTest {

    @Mock HistorialExpedienteExportService exportService;
    @Mock ExpedienteService expedienteService;
    @Mock CurrentUserService currentUserService;
    @Mock Authentication authentication;
    @Mock Usuario usuario;
    @InjectMocks HistorialExpedienteExportApiController controller;

    @Test
    void exportacionClienteConservaLaAudienciaRestringida() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(15L);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);
        when(expedienteService.buscarPorId(15L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(true);

        ResponseEntity<StreamingResponseBody> response = controller.exportarCliente(
                15L, CategoriaHistorial.DOCUMENTO,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4), authentication);
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("historial-expediente-15.csv");
        verify(exportService).exportarCsv(
                org.mockito.ArgumentMatchers.eq(15L), org.mockito.ArgumentMatchers.eq(CategoriaHistorial.DOCUMENTO),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 4)),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(java.io.OutputStream.class));
    }

    @Test
    void noPermiteExportarUnExpedienteAjeno() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(15L);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);
        when(expedienteService.buscarPorId(15L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(false);

        assertThatThrownBy(() -> controller.exportarAdmin(15L, null, null, null, authentication))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(exportService, never()).exportarCsv(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechazaFechasInvertidasAntesDeIniciarLaDescarga() {
        Expediente expediente = new Expediente();
        expediente.setId(15L);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);
        when(expedienteService.buscarPorId(15L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(true);
        when(usuario.getRolUsuario()).thenReturn(RolUsuario.ADMIN);

        assertThatThrownBy(() -> controller.exportarAdmin(
                15L, null, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 1), authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fecha hasta");
    }
    @Test
    void unClienteNoPuedeUsarLaExportacionAdministrativaAunqueElExpedienteSeaSuyo() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(15L);
        when(currentUserService.requireUser(authentication)).thenReturn(usuario);
        when(expedienteService.buscarPorId(15L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(true);
        when(usuario.getRolUsuario()).thenReturn(RolUsuario.CLIENTE);

        assertThatThrownBy(() -> controller.exportarAdmin(15L, null, null, null, authentication))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("administrador");
        verify(exportService, never()).exportarCsv(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }
}
