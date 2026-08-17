package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoLecturaIaItem;
import com.example.gestor_documental.enums.EstadoLecturaIaJob;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoLecturaIa;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import com.example.gestor_documental.model.SolicitudLecturaIaItem;
import com.example.gestor_documental.model.SolicitudLecturaIaJob;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.SolicitudLecturaIaItemRepository;
import com.example.gestor_documental.repository.SolicitudLecturaIaJobRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.service.SolicitudService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudLecturaIaJobServiceImplTest {

    @Mock SolicitudLecturaIaJobRepository jobRepository;
    @Mock SolicitudLecturaIaItemRepository itemRepository;
    @Mock SolicitudRepository solicitudRepository;
    @Mock DocumentoRepository documentoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock DocumentoIdentidadLecturaRepository identidadRepository;
    @Mock DocumentoRolesLecturaRepository rolesRepository;
    @Mock DocumentoVehiculoLecturaRepository vehiculoRepository;
    @Mock DocumentoIdentidadLecturaService identidadService;
    @Mock DocumentoRolesLecturaService rolesService;
    @Mock DocumentoVehiculoLecturaService vehiculoService;
    @Mock SolicitudDocumentacionIaService consolidacionService;
    @Mock SolicitudService solicitudService;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks SolicitudLecturaIaJobServiceImpl service;

    @AfterEach
    void cerrar() {
        service.cerrarExecutor();
    }

    @Test
    void reutilizaIdentidadPersistidaPorOtroProcesoEnLugarDeMarcarError() {
        long documentoId = 8891L;
        Usuario usuario = new Usuario();
        DocumentoIdentidadLectura lectura = identidad(documentoId, false, 0.98);
        when(identidadService.leerIdentidad(documentoId, false, usuario))
                .thenThrow(conflictoDuplicado("documento_identidad_lectura.idx_doc_identidad_documento"));
        when(identidadRepository.findByDocumentoId(documentoId)).thenReturn(Optional.of(lectura));

        SolicitudLecturaIaJobServiceImpl.ResultadoItem resultado = service.leer(
                new SolicitudLecturaIaJobServiceImpl.ItemContext(documentoId, TipoLecturaIa.IDENTIDAD, false),
                usuario);

        assertThat(resultado.estado()).isEqualTo(EstadoLecturaIaItem.COMPLETADO);
        assertThat(resultado.confianza()).isEqualTo(0.98);
        assertThat(resultado.mensaje()).isEqualTo(
                "Lectura ya completada por otro proceso; se reutilizo el resultado disponible.");
    }

    @Test
    void conservaElEstadoDeRevisionDeUnaLecturaConcurrente() {
        long documentoId = 8895L;
        Usuario usuario = new Usuario();
        DocumentoVehiculoLectura lectura = vehiculo(documentoId, true, 0.17);
        when(vehiculoService.leerVehiculo(documentoId, false, usuario))
                .thenThrow(conflictoDuplicado("documento_vehiculo_lectura.idx_doc_vehiculo_documento"));
        when(vehiculoRepository.findByDocumentoId(documentoId)).thenReturn(Optional.of(lectura));

        SolicitudLecturaIaJobServiceImpl.ResultadoItem resultado = service.leer(
                new SolicitudLecturaIaJobServiceImpl.ItemContext(documentoId, TipoLecturaIa.VEHICULO, false),
                usuario);

        assertThat(resultado.estado()).isEqualTo(EstadoLecturaIaItem.REQUIERE_REVISION);
        assertThat(resultado.confianza()).isEqualTo(0.17);
        assertThat(resultado.mensaje()).doesNotContain("Duplicate entry", "insert into", "constraint");
    }

    @Test
    void noExponeSqlEnElDetalleDeUnErrorDePersistencia() {
        DataIntegrityViolationException exception = conflictoDuplicado(
                "documento_identidad_lectura.idx_doc_identidad_documento");

        assertThat(service.mensajeSeguro(exception))
                .isEqualTo("No se pudo guardar la lectura. Vuelve a intentarlo.");
    }

    @Test
    void reparaAlArrancarLosErroresDuplicadosQueTienenLecturaPersistida() {
        long documentoId = 8895L;
        LocalDateTime fechaFinOriginal = LocalDateTime.of(2026, 8, 17, 14, 53, 39);
        Documento documento = documento(documentoId, TipoDocumento.FICHA_TECNICA);
        SolicitudLecturaIaJob job = new SolicitudLecturaIaJob();
        job.setId(111L);
        job.setEstado(EstadoLecturaIaJob.REQUIERE_REVISION);
        job.setTotalItems(1);
        job.setItemsError(1);
        job.setFechaFin(fechaFinOriginal);
        SolicitudLecturaIaItem item = new SolicitudLecturaIaItem();
        item.setId(337L);
        item.setJob(job);
        item.setDocumento(documento);
        item.setTipoLectura(TipoLecturaIa.VEHICULO);
        item.setEstado(EstadoLecturaIaItem.ERROR);
        item.setMensaje("could not execute statement: Duplicate entry '8895' for key lectura");
        DocumentoVehiculoLectura lectura = vehiculo(documentoId, true, 0.17);

        when(itemRepository.findByEstadoAndMensajeContainingIgnoreCase(
                EstadoLecturaIaItem.ERROR, "Duplicate entry")).thenReturn(java.util.List.of(item));
        when(vehiculoRepository.findByDocumentoId(documentoId)).thenReturn(Optional.of(lectura));
        when(jobRepository.findById(111L)).thenReturn(Optional.of(job));
        when(itemRepository.findByJobIdOrderById(111L)).thenReturn(java.util.List.of(item));
        when(jobRepository.save(any(SolicitudLecturaIaJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.repararErroresConcurrentes();

        assertThat(item.getEstado()).isEqualTo(EstadoLecturaIaItem.REQUIERE_REVISION);
        assertThat(item.getMensaje()).startsWith("Lectura ya completada por otro proceso");
        assertThat(job.getEstado()).isEqualTo(EstadoLecturaIaJob.REQUIERE_REVISION);
        assertThat(job.getItemsError()).isZero();
        assertThat(job.getItemsRevision()).isEqualTo(1);
        assertThat(job.getFechaFin()).isEqualTo(fechaFinOriginal);
        verify(itemRepository).save(item);
    }

    private DocumentoIdentidadLectura identidad(long documentoId, boolean revision, double confianza) {
        Documento documento = documento(documentoId, TipoDocumento.DNI);
        DocumentoIdentidadLectura lectura = new DocumentoIdentidadLectura();
        lectura.setDocumento(documento);
        lectura.setIdentificador("45863893F");
        lectura.setConfianzaGlobal(confianza);
        lectura.setRequiereRevision(revision);
        lectura.setModelo("gpt-test");
        return lectura;
    }

    private DocumentoVehiculoLectura vehiculo(long documentoId, boolean revision, double confianza) {
        Documento documento = documento(documentoId, TipoDocumento.FICHA_TECNICA);
        DocumentoVehiculoLectura lectura = new DocumentoVehiculoLectura();
        lectura.setDocumento(documento);
        lectura.setConfianzaGlobal(confianza);
        lectura.setRequiereRevision(revision);
        lectura.setModelo("gpt-test");
        return lectura;
    }

    private Documento documento(long id, TipoDocumento tipo) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setTipoDocumento(tipo);
        return documento;
    }

    private DataIntegrityViolationException conflictoDuplicado(String indice) {
        return new DataIntegrityViolationException("Duplicate entry para " + indice);
    }
}
