package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ExpedienteLecturaIaJobResponse;
import com.example.gestor_documental.enums.EstadoLecturaIaJob;
import com.example.gestor_documental.enums.EstadoLecturaIaItem;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteLecturaIaItem;
import com.example.gestor_documental.model.ExpedienteLecturaIaJob;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.*;
import com.example.gestor_documental.service.ExpedienteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpedienteLecturaIaJobServiceImplTest {
    @Mock ExpedienteLecturaIaJobRepository jobRepository;
    @Mock ExpedienteLecturaIaItemRepository itemRepository;
    @Mock ExpedienteRepository expedienteRepository;
    @Mock DocumentoRepository documentoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock DocumentoIdentidadLecturaRepository identidadRepository;
    @Mock DocumentoRolesLecturaRepository rolesRepository;
    @Mock DocumentoVehiculoLecturaRepository vehiculoRepository;
    @Mock ExpedienteDocumentacionActualizacionService actualizacionService;
    @Mock ExpedienteService expedienteService;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks ExpedienteLecturaIaJobServiceImpl service;

    @BeforeEach
    void iniciarSincronizacion() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cerrar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        service.cerrarExecutor();
    }

    @Test
    void creaUnTrabajoPersistenteConLosDocumentosCompatiblesPendientes() {
        Expediente expediente = expediente(7L);
        Usuario usuario = usuario(3L);
        Documento dni = documento(21L, TipoDocumento.DNI, expediente);
        Documento otros = documento(22L, TipoDocumento.OTROS, expediente);
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(true);
        when(jobRepository.findTopByExpedienteIdAndEstadoInOrderByFechaCreacionDescIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(documentoRepository.findByExpedienteId(7L)).thenReturn(List.of(dni, otros));
        when(itemRepository.existsByDocumentoIdAndEstadoIn(21L, List.of(EstadoLecturaIaItem.PENDIENTE, EstadoLecturaIaItem.PROCESANDO)))
                .thenReturn(false);
        when(identidadRepository.findByDocumentoId(21L)).thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(ExpedienteLecturaIaJob.class))).thenAnswer(invocation -> {
            ExpedienteLecturaIaJob job = invocation.getArgument(0);
            job.setId(50L);
            job.setFechaCreacion(LocalDateTime.now());
            return job;
        });

        ExpedienteLecturaIaJobResponse response = service.crear(7L, usuario, false, "MANUAL");

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getEstado()).isEqualTo("PENDIENTE");
        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getItems()).singleElement().extracting(ExpedienteLecturaIaJobResponse.Item::getDocumentoId)
                .isEqualTo(21L);
        verify(jobRepository).saveAndFlush(any(ExpedienteLecturaIaJob.class));
    }

    @Test
    void creaOtroTrabajoParaUnDocumentoNuevoAunqueHayaUnaLecturaActiva() {
        Expediente expediente = expediente(8L);
        Usuario usuario = usuario(4L);
        Documento anterior = documento(31L, TipoDocumento.DNI, expediente);
        Documento nuevo = documento(32L, TipoDocumento.PERMISO_CIRCULACION, expediente);
        ExpedienteLecturaIaJob activo = new ExpedienteLecturaIaJob();
        activo.setId(60L);
        activo.setExpediente(expediente);
        activo.setEstado(EstadoLecturaIaJob.PROCESANDO);
        activo.setOrigen("AUTO_SUBIDA");
        ExpedienteLecturaIaItem itemActivo = new ExpedienteLecturaIaItem();
        itemActivo.setDocumento(anterior);
        activo.addItem(itemActivo);
        activo.setTotalItems(1);

        when(expedienteRepository.findById(8L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, usuario)).thenReturn(true);
        when(jobRepository.findTopByExpedienteIdAndEstadoInOrderByFechaCreacionDescIdDesc(any(), any()))
                .thenReturn(Optional.of(activo));
        when(documentoRepository.findByExpedienteId(8L)).thenReturn(List.of(anterior, nuevo));
        when(itemRepository.existsByDocumentoIdAndEstadoIn(31L, List.of(EstadoLecturaIaItem.PENDIENTE, EstadoLecturaIaItem.PROCESANDO)))
                .thenReturn(true);
        when(itemRepository.existsByDocumentoIdAndEstadoIn(32L, List.of(EstadoLecturaIaItem.PENDIENTE, EstadoLecturaIaItem.PROCESANDO)))
                .thenReturn(false);
        when(vehiculoRepository.findByDocumentoId(32L)).thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(ExpedienteLecturaIaJob.class))).thenAnswer(invocation -> {
            ExpedienteLecturaIaJob job = invocation.getArgument(0);
            job.setId(61L);
            job.setFechaCreacion(LocalDateTime.now());
            return job;
        });

        ExpedienteLecturaIaJobResponse response = service.crear(8L, usuario, false, "AUTO_SUBIDA");

        assertThat(response.getId()).isEqualTo(61L);
        assertThat(response.getItems()).singleElement().extracting(ExpedienteLecturaIaJobResponse.Item::getDocumentoId)
                .isEqualTo(32L);
    }

    private Expediente expediente(Long id) {
        Expediente expediente = new Expediente();
        expediente.setId(id);
        return expediente;
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Documento documento(Long id, TipoDocumento tipo, Expediente expediente) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setTipoDocumento(tipo);
        documento.setExpediente(expediente);
        return documento;
    }
}
