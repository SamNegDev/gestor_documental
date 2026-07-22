package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.CorreccionClasificacionDocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.OcrPdfService;
import com.example.gestor_documental.service.PdfSplitService;
import com.example.gestor_documental.service.SolicitudService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock DocumentoRepository documentoRepository;
    @Mock ExpedienteRepository expedienteRepository;
    @Mock IncidenciaRepository incidenciaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ClienteInteresadoRepository clienteInteresadoRepository;
    @Mock InteresadoRepository interesadoRepository;
    @Mock SolicitudRepository solicitudRepository;
    @Mock CorreccionClasificacionDocumentoRepository correccionRepository;
    @Mock RequisitoDocumentalExpedienteRepository requisitoRepository;
    @Mock OperacionExpedienteRepository operacionRepository;
    @Mock ExpedienteService expedienteService;
    @Mock SolicitudService solicitudService;
    @Mock OcrPdfService ocrPdfService;
    @Mock PdfSplitService pdfSplitService;
    @Mock HistorialCambioService historialCambioService;
    @Spy TransactionalFileService transactionalFileService = new TransactionalFileService();

    @InjectMocks
    DocumentoServiceImpl service;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void listarPorClienteDevuelveSoloDocumentacionRecurrentePropia() {
        Documento recurrente = documento(10L, "cif.pdf", "CIF.PDF");
        when(documentoRepository.findByClienteIdAndExpedienteIsNullAndSolicitudIsNullAndInteresadoIsNullOrderByFechaSubidaDesc(4L))
                .thenReturn(List.of(recurrente));

        List<Documento> resultado = service.listarPorCliente(4L);

        assertThat(resultado).containsExactly(recurrente);
        verify(documentoRepository).findByClienteIdAndExpedienteIsNullAndSolicitudIsNullAndInteresadoIsNullOrderByFechaSubidaDesc(4L);
    }

    @Test
    void unaUnionRevertidaConservaLosArchivosOriginales() throws Exception {
        Documento principal = documento(1L, "principal.pdf", "PRINCIPAL.PDF");
        Documento secundario = documento(2L, "secundario.jpeg", "SECUNDARIO.JPEG");
        Path rutaPrincipal = tempDir.resolve(principal.getNombreArchivo());
        Path rutaSecundario = tempDir.resolve(secundario.getNombreArchivo());
        Files.writeString(rutaPrincipal, "principal");
        Files.writeString(rutaSecundario, "secundario");
        when(documentoRepository.findByIdConRelaciones(1L)).thenReturn(Optional.of(principal));
        when(documentoRepository.findByIdConRelaciones(2L)).thenReturn(Optional.of(secundario));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn("unido".getBytes());
        iniciarTransaccion();

        service.unirDocumentos(1L, List.of(2L), null, null, null, null);
        Path rutaNueva = tempDir.resolve(principal.getNombreArchivo());

        assertThat(rutaPrincipal).exists();
        assertThat(rutaSecundario).exists();
        assertThat(rutaNueva).exists();
        verify(correccionRepository).desvincularDocumento(2L);
        verify(documentoRepository).delete(secundario);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(rutaPrincipal).exists();
        assertThat(rutaSecundario).exists();
        assertThat(rutaNueva).doesNotExist();
    }

    @Test
    void cuentaYRenderizaUnJpegComoDocumentoDeUnaPagina() throws Exception {
        Documento imagen = documento(3L, "imagen.jpeg", "IMAGEN.JPEG");
        BufferedImage contenido = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(contenido, "jpeg", tempDir.resolve(imagen.getNombreArchivo()).toFile());
        when(documentoRepository.findByIdConRelaciones(3L)).thenReturn(Optional.of(imagen));

        int paginas = service.contarPaginasDocumento(3L, null);
        byte[] preview = service.renderizarPaginaDocumento(3L, 1, null);

        assertThat(paginas).isEqualTo(1);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(preview))).isNotNull();
    }

    private Documento documento(Long id, String nombreFisico, String nombreOriginal) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setNombreArchivo(nombreFisico);
        documento.setNombreArchivoOriginal(nombreOriginal);
        documento.setTipoDocumento(TipoDocumento.OTROS);
        return documento;
    }

    private void iniciarTransaccion() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completar(int estado) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(estado);
        }
    }
}
