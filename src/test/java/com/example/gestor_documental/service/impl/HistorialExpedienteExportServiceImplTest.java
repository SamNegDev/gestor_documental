package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.HistorialCambioDetalle;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.HistorialCambioExportRepository;
import com.example.gestor_documental.repository.HistorialCambioDetalleRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class HistorialExpedienteExportServiceImplTest {

    @Mock HistorialCambioExportRepository repository;
    @Mock HistorialCambioDetalleRepository detalleRepository;

    @Test
    void generaCsvUtf8PaginadoYNeutralizaFormulas() throws Exception {
        HistorialCambio cambio = new HistorialCambio();
        cambio.setId(8L);
        cambio.setFechaCambio(LocalDateTime.of(2026, 8, 4, 10, 15));
        cambio.setAccion("CAMBIO ESTADO");
        cambio.setDescripcion("=HIPERVINCULO(\"https://example.test\")");
        cambio.setCategoria(CategoriaHistorial.ESTADO);
        cambio.setTipoActividad(TipoActividadHistorial.CAMBIO);
        cambio.setAudiencia(AudienciaHistorial.AMBOS);
        cambio.setUsuario(new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true));
        when(repository.buscarParaExportar(
                eq(12L), eq(CategoriaHistorial.ESTADO),
                eq(LocalDate.of(2026, 8, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 8, 5).atStartOfDay()),
                eq(true), any(), eq(TipoActividadHistorial.COMUNICACION), any(), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(cambio)));
        HistorialExpedienteExportServiceImpl service = new HistorialExpedienteExportServiceImpl(repository, detalleRepository);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.exportarCsv(
                12L, CategoriaHistorial.ESTADO,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4),
                true, output);

        String csv = output.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\ufeffFecha;Accion;Descripcion;Usuario;Categoria;Tipo de actividad\r\n");
        assertThat(csv).contains("\"04/08/2026 10:15:00\"");
        assertThat(csv).contains("\"'=HIPERVINCULO(\"\"https://example.test\"\")\"");
        assertThat(csv).contains("\"Ada Lovelace\";\"ESTADO\";\"CAMBIO\"");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarParaExportar(
                eq(12L), eq(CategoriaHistorial.ESTADO), any(), any(), eq(true),
                any(), eq(TipoActividadHistorial.COMUNICACION), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(500);
        verify(detalleRepository, never()).findByHistorialCambioIdInOrderByHistorialCambioIdAscIdAsc(any());
    }

    @Test
    void rechazaUnIntervaloInvertidoAntesDeConsultar() {
        HistorialExpedienteExportServiceImpl service = new HistorialExpedienteExportServiceImpl(repository, detalleRepository);

        assertThatThrownBy(() -> service.exportarCsv(
                12L, null,
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 1),
                false, new ByteArrayOutputStream()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fecha hasta");
        verify(repository, never()).buscarParaExportar(any(), any(), any(), any(),
                eq(false), any(), any(), any(), any());
    }
    @Test
    void incluyeValoresEstructuradosSoloEnExportacionAdministrativa() throws Exception {
        HistorialCambio cambio = new HistorialCambio();
        cambio.setId(9L);
        cambio.setFechaCambio(LocalDateTime.of(2026, 8, 4, 11, 0));
        cambio.setAccion("CAMBIO ESTADO");
        cambio.setDescripcion("Cambio funcional visible.");
        cambio.setCategoria(CategoriaHistorial.ESTADO);
        cambio.setTipoActividad(TipoActividadHistorial.CAMBIO);

        HistorialCambioDetalle detalle = new HistorialCambioDetalle();
        detalle.setHistorialCambio(cambio);
        detalle.setCampo("estado");
        detalle.setEtiqueta("Estado");
        detalle.setValorAnterior("EN_TRAMITE");
        detalle.setValorPosterior("FINALIZADO");

        when(repository.buscarParaExportar(
                eq(12L), eq(null), eq(null), eq(null), eq(false), any(),
                eq(TipoActividadHistorial.COMUNICACION), any(), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(cambio)));
        when(detalleRepository.findByHistorialCambioIdInOrderByHistorialCambioIdAscIdAsc(List.of(9L)))
                .thenReturn(List.of(detalle));
        HistorialExpedienteExportServiceImpl service =
                new HistorialExpedienteExportServiceImpl(repository, detalleRepository);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.exportarCsv(12L, null, null, null, false, output);

        String csv = output.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\ufeffFecha;Accion;Descripcion;Usuario;Categoria;Tipo de actividad;Valores anteriores;Valores posteriores\r\n");
        assertThat(csv).contains("\"Estado: EN_TRAMITE\";\"Estado: FINALIZADO\"");
    }
}
