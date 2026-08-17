package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.CorreccionClasificacionDocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudLecturaIaItemRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
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
    @Mock SolicitudLecturaIaItemRepository lecturaIaItemRepository;
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
        ReflectionTestUtils.setField(service, "allowedExtensions", "pdf,jpg,jpeg,png");
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void aplicaElOrdenDocumentalOperativoAlExpedienteCompleto() {
        assertThat(List.of(
                TipoDocumento.MODELO_620, TipoDocumento.DNI, TipoDocumento.FACTURA,
                TipoDocumento.OTROS, TipoDocumento.MANDATO, TipoDocumento.PERMISO_CIRCULACION,
                TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.INFORME_DGT,
                TipoDocumento.CAMBIO_TITULARIDAD, TipoDocumento.FICHA_TECNICA, TipoDocumento.CIF)
                .stream()
                .sorted(java.util.Comparator.comparingInt(DocumentoServiceImpl::ordenDocumento)))
                .containsExactly(
                        TipoDocumento.FACTURA, TipoDocumento.CONTRATO_COMPRAVENTA,
                        TipoDocumento.CAMBIO_TITULARIDAD, TipoDocumento.MANDATO, TipoDocumento.OTROS,
                        TipoDocumento.DNI, TipoDocumento.CIF, TipoDocumento.PERMISO_CIRCULACION,
                        TipoDocumento.FICHA_TECNICA, TipoDocumento.INFORME_DGT, TipoDocumento.MODELO_620);
    }

    @Test
    void creaExpedienteCompletoAlSubirElPrimerDocumentoIndividual() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(7L);
        expediente.setMatricula("1234ABC");
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "dni.pdf", "application/pdf", "pagina-individual".getBytes());
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, null)).thenReturn(true);
        when(documentoRepository.findFirstByExpedienteIdAndTipoDocumentoOrderByFechaSubidaDesc(
                7L, TipoDocumento.EXPEDIENTE_COMPLETO)).thenReturn(Optional.empty());
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(1));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.guardarParaExpediente(7L, archivo, TipoDocumento.DNI, null);

        org.mockito.ArgumentCaptor<Documento> documentos = org.mockito.ArgumentCaptor.forClass(Documento.class);
        verify(documentoRepository, times(3)).save(documentos.capture());
        assertThat(documentos.getAllValues()).extracting(Documento::getTipoDocumento)
                .contains(TipoDocumento.DNI, TipoDocumento.EXPEDIENTE_COMPLETO);
        Documento completo = documentos.getAllValues().stream()
                .filter(documento -> documento.getTipoDocumento() == TipoDocumento.EXPEDIENTE_COMPLETO)
                .findFirst().orElseThrow();
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(1);
    }

    @Test
    void anadeElDocumentoIndividualAlExpedienteCompletoExistente() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(8L);
        expediente.setMatricula("5678DEF");
        Documento completo = documento(20L, "completo.pdf", "5678DEF_EXPEDIENTE_COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        completo.setExpediente(expediente);
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(1));
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "permiso.jpg", "image/jpeg", "pagina-nueva".getBytes());
        when(expedienteRepository.findById(8L)).thenReturn(Optional.of(expediente));
        when(expedienteService.tienePermisoExpediente(expediente, null)).thenReturn(true);
        when(documentoRepository.findFirstByExpedienteIdAndTipoDocumentoOrderByFechaSubidaDesc(
                8L, TipoDocumento.EXPEDIENTE_COMPLETO)).thenReturn(Optional.of(completo));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(2));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.guardarParaExpediente(8L, archivo, TipoDocumento.PERMISO_CIRCULACION, null);

        assertThat(completo.getNombreArchivo()).isNotEqualTo("completo.pdf");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(2);
        verify(pdfSplitService).unirDocumentos(anyList());

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(tempDir.resolve("completo.pdf")).exists();
        assertThat(tempDir.resolve(completo.getNombreArchivo())).doesNotExist();
    }
    @Test
    void eliminarDocumentoSeparadoRetiraSusPaginasDelExpedienteCompleto() throws Exception {
        Expediente expediente = new Expediente();
        expediente.setId(9L);
        expediente.setMatricula("9012GHI");
        Documento completo = documento(30L, "completo-3.pdf", "9012GHI_EXPEDIENTE_COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        completo.setExpediente(expediente);
        Documento eliminado = documento(31L, "eliminado.pdf", "PAGINA_EN_BLANCO.PDF");
        eliminado.setExpediente(expediente);
        eliminado.setExpedienteCompletoOrigen(completo);
        eliminado.setPaginasExpedienteCompleto("1");
        Documento posterior = documento(32L, "posterior.pdf", "DNI.PDF");
        posterior.setExpediente(expediente);
        posterior.setExpedienteCompletoOrigen(completo);
        posterior.setPaginasExpedienteCompleto("2");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(3));
        Files.write(tempDir.resolve(eliminado.getNombreArchivo()), pdfConPaginas(1));
        when(documentoRepository.findByIdConRelaciones(31L)).thenReturn(Optional.of(eliminado));
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(30L))
                .thenReturn(List.of(eliminado, posterior));
        when(pdfSplitService.eliminarPaginas(any(), anyList())).thenReturn(pdfConPaginas(2));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.eliminar(31L, null);

        assertThat(posterior.getPaginasExpedienteCompleto()).isEqualTo("1");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(2);
        verify(documentoRepository).delete(eliminado);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(tempDir.resolve("completo-3.pdf")).exists();
    }
    @Test
    void listarPorClienteConservaDocumentacionRecurrenteTrasVincularIdentidad() {
        Documento recurrente = documento(10L, "cif.pdf", "CIF.PDF");
        recurrente.setInteresado(new Interesado("B38436556", "EMPRESA CLIENTE"));
        when(documentoRepository.findByClienteIdAndExpedienteIsNullAndSolicitudIsNullOrderByFechaSubidaDesc(4L))
                .thenReturn(List.of(recurrente));

        List<Documento> resultado = service.listarPorCliente(4L);

        assertThat(resultado).containsExactly(recurrente);
        assertThat(resultado.get(0).getInteresado()).isNotNull();
        verify(documentoRepository).findByClienteIdAndExpedienteIsNullAndSolicitudIsNullOrderByFechaSubidaDesc(4L);
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
        verify(lecturaIaItemRepository).reasignarDocumento(2L, 1L);
        verify(correccionRepository).desvincularDocumento(2L);
        verify(documentoRepository).delete(secundario);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(rutaPrincipal).exists();
        assertThat(rutaSecundario).exists();
        assertThat(rutaNueva).doesNotExist();
    }

    @Test
    void extraerPaginasEnSolicitudConservaElDocumentoEnElExpedienteCompleto() throws Exception {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(40L);
        solicitud.setMatricula("3213BPP");
        Documento completo = documento(41L, "completo-solicitud.pdf", "3213BPP.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        completo.setSolicitud(solicitud);
        Documento original = documento(42L, "dos-identidades.pdf", "DOS_IDENTIDADES.PDF");
        original.setSolicitud(solicitud);
        original.setExpedienteCompletoOrigen(completo);
        original.setPaginasExpedienteCompleto("0,1");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(2));
        Files.write(tempDir.resolve(original.getNombreArchivo()), pdfConPaginas(2));
        AtomicReference<Documento> generadoRef = new AtomicReference<>();
        when(documentoRepository.findByIdConRelaciones(42L)).thenReturn(Optional.of(original));
        when(solicitudService.tienePermisoSolicitud(solicitud, null)).thenReturn(true);
        when(pdfSplitService.parseRangoPaginas("2", 2)).thenReturn(List.of(1));
        when(pdfSplitService.extraerPaginas(any(), anyList())).thenReturn(pdfConPaginas(1));
        when(pdfSplitService.eliminarPaginas(any(), anyList())).thenReturn(pdfConPaginas(1));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(2));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> {
            Documento guardado = invocation.getArgument(0);
            if (guardado.getId() == null) {
                guardado.setId(43L);
                generadoRef.set(guardado);
            }
            return guardado;
        });
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(41L))
                .thenAnswer(invocation -> List.of(original, generadoRef.get()));
        iniciarTransaccion();

        service.extraerPaginasDocumento(42L, "2", TipoDocumento.DNI, "DNI_DELFINO.PDF", null, null);

        Documento generado = generadoRef.get();
        assertThat(generado).isNotNull();
        assertThat(generado.getExpedienteCompletoOrigen()).isSameAs(completo);
        assertThat(original.getPaginasExpedienteCompleto()).isEqualTo("0");
        assertThat(generado.getPaginasExpedienteCompleto()).isEqualTo("1");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(2);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    void recortarUnDocumentoReconstruyeElExpedienteCompletoConLasPaginasRestantes() throws Exception {
        Documento completo = documento(50L, "completo-recorte.pdf", "RECORTE_COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        Documento recortado = documento(51L, "recortado.pdf", "RECORTADO.PDF");
        recortado.setExpedienteCompletoOrigen(completo);
        recortado.setPaginasExpedienteCompleto("0,1,2");
        Documento posterior = documento(52L, "posterior-recorte.pdf", "DNI.PDF");
        posterior.setTipoDocumento(TipoDocumento.DNI);
        posterior.setExpedienteCompletoOrigen(completo);
        posterior.setPaginasExpedienteCompleto("3");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(4));
        Files.write(tempDir.resolve(recortado.getNombreArchivo()), pdfConPaginas(3));
        Files.write(tempDir.resolve(posterior.getNombreArchivo()), pdfConPaginas(1));
        when(documentoRepository.findByIdConRelaciones(51L)).thenReturn(Optional.of(recortado));
        when(pdfSplitService.parseRangoPaginas("2", 3)).thenReturn(List.of(1));
        when(pdfSplitService.eliminarPaginas(any(), anyList())).thenReturn(pdfConPaginas(2));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(3));
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(50L))
                .thenReturn(List.of(recortado, posterior));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.eliminarPaginasDocumento(51L, "2", null);

        assertThat(recortado.getPaginasExpedienteCompleto()).isEqualTo("0,1");
        assertThat(posterior.getPaginasExpedienteCompleto()).isEqualTo("2");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(recortado.getNombreArchivo())))).isEqualTo(2);
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(3);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    void unirDocumentosVinculadosReconstruyeElCompletoYConservaLosTrabajosIa() throws Exception {
        Documento completo = documento(60L, "completo-union.pdf", "UNION_COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        Documento principal = documento(61L, "principal-union.pdf", "PRINCIPAL.PDF");
        principal.setExpedienteCompletoOrigen(completo);
        principal.setPaginasExpedienteCompleto("0");
        Documento secundario = documento(62L, "secundario-union.pdf", "SECUNDARIO.PDF");
        secundario.setTipoDocumento(TipoDocumento.DNI);
        secundario.setExpedienteCompletoOrigen(completo);
        secundario.setPaginasExpedienteCompleto("1");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(2));
        Files.write(tempDir.resolve(principal.getNombreArchivo()), pdfConPaginas(1));
        Files.write(tempDir.resolve(secundario.getNombreArchivo()), pdfConPaginas(1));
        when(documentoRepository.findByIdConRelaciones(61L)).thenReturn(Optional.of(principal));
        when(documentoRepository.findByIdConRelaciones(62L)).thenReturn(Optional.of(secundario));
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(60L)).thenReturn(List.of(principal));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(2));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.unirDocumentos(61L, List.of(62L), TipoDocumento.DNI, "DNI_UNIDO.PDF", null, null);

        assertThat(principal.getExpedienteCompletoOrigen()).isSameAs(completo);
        assertThat(principal.getPaginasExpedienteCompleto()).isEqualTo("0,1");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(principal.getNombreArchivo())))).isEqualTo(2);
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(2);
        verify(lecturaIaItemRepository).reasignarDocumento(62L, 61L);
        verify(documentoRepository).delete(secundario);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    void recomponerExcluyeYDesvinculaElExpedienteCompletoAutorreferenciado() throws Exception {
        Documento completo = documento(80L, "completo-corrupto.pdf", "COMPLETO_CORRUPTO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        completo.setExpedienteCompletoOrigen(completo);
        completo.setPaginasExpedienteCompleto("0,1");
        Documento dni = documento(81L, "dni-valido.pdf", "DNI_VALIDO.PDF");
        dni.setTipoDocumento(TipoDocumento.OTROS);
        dni.setExpedienteCompletoOrigen(completo);
        dni.setPaginasExpedienteCompleto("2");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(2));
        Files.write(tempDir.resolve(dni.getNombreArchivo()), pdfConPaginas(1));
        when(documentoRepository.findByIdConRelaciones(81L)).thenReturn(Optional.of(dni));
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(80L))
                .thenReturn(List.of(completo, dni));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(1));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.actualizarDocumento(81L, TipoDocumento.DNI, null, null, null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<byte[]>> contenidos = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pdfSplitService).unirDocumentos(contenidos.capture());
        assertThat(contenidos.getValue()).hasSize(1);
        assertThat(completo.getExpedienteCompletoOrigen()).isNull();
        assertThat(completo.getPaginasExpedienteCompleto()).isNull();
        assertThat(dni.getPaginasExpedienteCompleto()).isEqualTo("0");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(1);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(tempDir.resolve("completo-corrupto.pdf")).exists();
    }

    @Test
    void rechazaUnirElExpedienteCompletoConUnoDeSusDerivados() {
        Documento completo = documento(90L, "completo.pdf", "COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        Documento derivado = documento(91L, "derivado.pdf", "DERIVADO.PDF");
        derivado.setExpedienteCompletoOrigen(completo);
        when(documentoRepository.findByIdConRelaciones(90L)).thenReturn(Optional.of(completo));
        when(documentoRepository.findByIdConRelaciones(91L)).thenReturn(Optional.of(derivado));

        assertThatThrownBy(() -> service.unirDocumentos(90L, List.of(91L), null, null, null, null))
                .isInstanceOf(com.example.gestor_documental.exception.OperacionInvalidaException.class)
                .hasMessageContaining("expediente completo");
    }

    @Test
    void reclasificarDocumentoReordenaLasPaginasDelExpedienteCompleto() throws Exception {
        Documento completo = documento(70L, "completo-clasificacion.pdf", "CLASIFICACION_COMPLETO.PDF");
        completo.setTipoDocumento(TipoDocumento.EXPEDIENTE_COMPLETO);
        Documento reclasificado = documento(71L, "reclasificado.pdf", "OTROS.PDF");
        reclasificado.setExpedienteCompletoOrigen(completo);
        reclasificado.setPaginasExpedienteCompleto("0");
        Documento dni = documento(72L, "dni-clasificacion.pdf", "DNI.PDF");
        dni.setTipoDocumento(TipoDocumento.DNI);
        dni.setExpedienteCompletoOrigen(completo);
        dni.setPaginasExpedienteCompleto("1");
        Files.write(tempDir.resolve(completo.getNombreArchivo()), pdfConPaginas(2));
        Files.write(tempDir.resolve(reclasificado.getNombreArchivo()), pdfConPaginas(1));
        Files.write(tempDir.resolve(dni.getNombreArchivo()), pdfConPaginas(1));
        when(documentoRepository.findByIdConRelaciones(71L)).thenReturn(Optional.of(reclasificado));
        when(documentoRepository.findByExpedienteCompletoOrigenIdOrderById(70L))
                .thenReturn(List.of(reclasificado, dni));
        when(pdfSplitService.unirDocumentos(anyList())).thenReturn(pdfConPaginas(2));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        iniciarTransaccion();

        service.actualizarDocumento(71L, TipoDocumento.PERMISO_CIRCULACION, null, null, null);

        assertThat(dni.getPaginasExpedienteCompleto()).isEqualTo("0");
        assertThat(reclasificado.getPaginasExpedienteCompleto()).isEqualTo("1");
        assertThat(contarPaginas(Files.readAllBytes(tempDir.resolve(completo.getNombreArchivo())))).isEqualTo(2);

        completar(TransactionSynchronization.STATUS_ROLLED_BACK);
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

    @Test
    void aislaDocumentosSegunElClienteActivo() {
        Cliente clienteA = new Cliente();
        clienteA.setId(10L);
        Cliente clienteB = new Cliente();
        clienteB.setId(20L);
        Usuario usuario = new Usuario("Cliente", "Multiple", "cliente@test.local", "secret", RolUsuario.CLIENTE, true);
        usuario.getClientesAutorizados().addAll(List.of(clienteA, clienteB));
        usuario.setCliente(clienteA);
        Documento documento = documento(9L, "cliente-b.pdf", "CLIENTE-B.PDF");
        documento.setCliente(clienteB);
        when(documentoRepository.findByIdConRelaciones(9L)).thenReturn(Optional.of(documento));

        assertThatThrownBy(() -> service.obtenerDocumentoConPermiso(9L, usuario))
                .isInstanceOf(AccesoDenegadoException.class);

        usuario.setCliente(clienteB);
        assertThat(service.obtenerDocumentoConPermiso(9L, usuario)).isSameAs(documento);
    }

    private int contarPaginas(byte[] contenido) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument.load(contenido)) {
            return pdf.getNumberOfPages();
        }
    }
    private byte[] pdfConPaginas(int totalPaginas) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            for (int pagina = 0; pagina < totalPaginas; pagina++) {
                pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            pdf.save(output);
            return output.toByteArray();
        }
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
