package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.CorreccionClasificacionDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.CorreccionClasificacionDocumentoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.*;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentoServiceImpl implements DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoServiceImpl.class);
    private static final String ACCION_CARGAR_DOCUMENTO = "CARGAR DOCUMENTO";

    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final DocumentoRolesLecturaRepository documentoRolesLecturaRepository;
    private final DocumentoVehiculoLecturaRepository documentoVehiculoLecturaRepository;
    private final ExpedienteRepository expedienteRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final InteresadoRepository interesadoRepository;
    private final SolicitudRepository solicitudRepository;
    private final CorreccionClasificacionDocumentoRepository correccionClasificacionDocumentoRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoDocumentalRepository;
    private final OperacionExpedienteRepository operacionExpedienteRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;
    private final OcrPdfService ocrPdfService;
    private final PdfSplitService pdfSplitService;
    private final HistorialCambioService historialCambioService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.allowed-extensions:pdf,jpg,jpeg,png}")
    private String allowedExtensions;

    @Override
    public void guardar(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento) {
        guardarParaExpediente(expedienteId, archivo, tipoDocumento, null);
    }

    /**
     * Guarda físicamente un PDF asociado a un Expediente.
     * Caso crítico: Si el archivo se sube con el prefijo "EXPEDIENTE_COMPLETO",
     * opera como puente automático.
     * No solo guarda el original, sino que desencadena el servicio de OCR para
     * detectar y despiezar
     * (split) automáticamente las partes internas del documento generando un
     * histórico de registros
     * asíncronos bajo el mismo documento base.
     */
    @Override
    public Documento guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento,
            Usuario usuario) {
        return guardarParaExpediente(expedienteId, archivo, tipoDocumento, null, usuario);
    }

    @Override
    public Documento guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento,
            Long operacionId, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }

        try {
            Expediente expediente = expedienteRepository.findById(expedienteId)
                    .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

            if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para subir documentos a este expediente");
            }
            OperacionExpediente operacion = resolverOperacionExpediente(expediente, operacionId);

            if (TipoDocumento.EXPEDIENTE_COMPLETO.equals(tipoDocumento)) {
                Documento docOriginal = guardarExpedienteCompletoOriginalParaExpediente(expedienteId, archivo, operacionId, usuario);
                procesarExpedienteCompletoDocumento(docOriginal.getId(), usuario);
                return docOriginal;
            }
            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setExpediente(expediente);
            doc.setOperacion(operacion);

            documentoRepository.save(doc);
            registrarCargaDocumentoExpediente(expediente, doc, usuario);
            return doc;


        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    @Transactional
    public Documento guardarGeneradoParaExpediente(Long expedienteId, byte[] contenido, TipoDocumento tipoDocumento,
            String nombreArchivoOriginal, String descripcion, Usuario usuario) {
        if (contenido == null || contenido.length == 0) {
            throw new OperacionInvalidaException("El documento generado esta vacio");
        }
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para generar documentos en este expediente");
        }
        try {
            Documento documento = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
            documento.setDescripcionArchivo(descripcion);
            documento.setExpediente(expediente);
            documentoRepository.save(documento);
            registrarCargaDocumentoExpediente(expediente, documento, usuario);
            return documento;
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo guardar el documento generado", exception);
        }
    }

    @Override
    @Transactional
    public Documento guardarGeneradoParaSolicitud(Long solicitudId, byte[] contenido, TipoDocumento tipoDocumento,
            String nombreArchivoOriginal, String descripcion, Usuario usuario) {
        if (contenido == null || contenido.length == 0) {
            throw new OperacionInvalidaException("El documento generado esta vacio");
        }
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para generar documentos en esta solicitud");
        }
        try {
            Documento documento = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
            documento.setDescripcionArchivo(descripcion);
            documento.setSolicitud(solicitud);
            documentoRepository.save(documento);
            registrarCargaDocumentoSolicitud(solicitud, documento, usuario);
            return documento;
        } catch (IOException exception) {
            throw new RuntimeException("No se pudo guardar el documento generado", exception);
        }
    }

    @Override
    @Transactional
    public Documento guardarExpedienteCompletoOriginalParaExpediente(Long expedienteId, MultipartFile archivo, Long operacionId, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        validarExpedienteCompletoPdf(archivo.getOriginalFilename());

        try {
            Expediente expediente = expedienteRepository.findById(expedienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
            if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para subir documentos a este expediente");
            }
            OperacionExpediente operacion = resolverOperacionExpediente(expediente, operacionId);

            Documento docOriginal = construirDocumentoBase(archivo, TipoDocumento.EXPEDIENTE_COMPLETO, usuario);
            docOriginal.setExpediente(expediente);
            docOriginal.setOperacion(operacion);
            documentoRepository.save(docOriginal);
            return docOriginal;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el expediente completo", e);
        }
    }

    @Override
    @Transactional
    public int procesarExpedienteCompletoDocumento(Long documentoId, Usuario usuario) {
        Documento docOriginal = obtenerDocumentoConPermiso(documentoId, usuario);
        if (docOriginal.getExpediente() == null) {
            throw new OperacionInvalidaException("El documento no pertenece a un expediente");
        }
        if (docOriginal.getTipoDocumento() != TipoDocumento.EXPEDIENTE_COMPLETO) {
            throw new OperacionInvalidaException("El documento no es un expediente completo");
        }
        validarExpedienteCompletoPdf(docOriginal.getNombreArchivoOriginal());

        Path rutaOriginal = obtenerCarpetaUploads().resolve(docOriginal.getNombreArchivo()).normalize();
        Path carpetaUploads = obtenerCarpetaUploads();
        if (!rutaOriginal.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        try {
            if (!Files.exists(rutaOriginal)) {
                throw new RecursoNoEncontradoException("El archivo fisico del documento no existe");
            }

            byte[] pdfOriginal = Files.readAllBytes(rutaOriginal);
            log.info("OCR_DIAG separacion-inicio tipo=EXPEDIENTE documentoId={} expedienteId={} archivo={} bytes={}",
                    docOriginal.getId(),
                    docOriginal.getExpediente().getId(),
                    docOriginal.getNombreArchivoOriginal(),
                    pdfOriginal.length);
            MultipartFile archivoProcesable = new PathMultipartFile(
                    rutaOriginal,
                    docOriginal.getNombreArchivoOriginal(),
                    "application/pdf"
            );
            List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivoProcesable);
            log.info("OCR_DIAG separacion-detectados tipo=EXPEDIENTE documentoId={} expedienteId={} bloques={}",
                    docOriginal.getId(),
                    docOriginal.getExpediente().getId(),
                    resumenDetectados(documentosDetectados));

            if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                log.warn("OCR_DIAG separacion-sin-detectados tipo=EXPEDIENTE documentoId={} expedienteId={}",
                        docOriginal.getId(),
                        docOriginal.getExpediente().getId());
                registrarProcesamientoExpedienteCompleto(docOriginal.getExpediente(), docOriginal, usuario, 0);
                return 0;
            }

            int generados = 0;
            for (DocumentoDetectadoDto detectado : documentosDetectados) {
                byte[] pdfSeparado = pdfSplitService.extraerPaginas(pdfOriginal, detectado.getPaginas());
                Documento generado = guardarDocumentoGeneradoParaExpediente(
                        docOriginal.getExpediente(),
                        pdfSeparado,
                        detectado.getTipoDocumento(),
                        usuario,
                        docOriginal.getExpediente().getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf",
                        docOriginal.getOperacion(),
                        false);
                log.info("OCR_DIAG separacion-generado tipo=EXPEDIENTE originalId={} generadoId={} expedienteId={} tipoDocumento={} paginas={} bytes={}",
                        docOriginal.getId(),
                        generado.getId(),
                        docOriginal.getExpediente().getId(),
                        detectado.getTipoDocumento(),
                        paginasHumanas(detectado.getPaginas()),
                        pdfSeparado.length);
                generados++;
            }
            registrarProcesamientoExpedienteCompleto(docOriginal.getExpediente(), docOriginal, usuario, generados);
            return generados;
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el expediente completo", e);
        }
    }

    @Override
    @Transactional
    public Documento guardarParaIncidencia(Long incidenciaId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }

        try {
            Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));

            Expediente expediente = incidencia.getExpediente();
            if (expediente == null) {
                throw new OperacionInvalidaException("La incidencia no pertenece a un expediente.");
            }

            if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para aportar documentos a esta incidencia");
            }

            Documento documento = construirDocumentoBase(archivo, tipoDocumento != null ? tipoDocumento : TipoDocumento.DOCUMENTO_INCIDENCIA, usuario);
            documento.setExpediente(expediente);
            documento.setIncidencia(incidencia);
            documentoRepository.save(documento);

            registrarCargaDocumentoExpediente(expediente, documento, usuario);
            marcarIncidenciaEnRevisionSiProcede(expediente, usuario);
            return documento;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    @Transactional
    public Documento vincularAIncidencia(Long incidenciaId, Long documentoId, Usuario usuario) {
        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));

        Expediente expediente = incidencia.getExpediente();
        if (expediente == null || documento.getExpediente() == null
                || !documento.getExpediente().getId().equals(expediente.getId())) {
            throw new OperacionInvalidaException("El documento no pertenece al expediente de la incidencia.");
        }

        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para vincular documentos a esta incidencia");
        }

        documento.setIncidencia(incidencia);
        if (documento.getTipoDocumento() == TipoDocumento.OTROS) {
            documento.setTipoDocumento(TipoDocumento.DOCUMENTO_INCIDENCIA);
        }
        Documento guardado = documentoRepository.save(documento);

        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                "DOCUMENTO INCIDENCIA",
                "Documento vinculado a incidencia: " + documento.getNombreArchivoOriginal()
        );
        marcarIncidenciaEnRevisionSiProcede(expediente, usuario);
        return guardado;
    }

    @Override
    @Transactional
    public Documento guardarParaCliente(Long clienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        if (usuario == null || usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo un administrador puede subir documentacion del cliente");
        }

        try {
            com.example.gestor_documental.model.Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
            Documento documento = construirDocumentoBase(archivo, tipoDocumento, usuario);
            documento.setCliente(cliente);
            documentoRepository.save(documento);
            return documento;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    @Transactional
    public Documento guardarParaInteresadoHabitual(Long clienteId, Long interesadoId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        validarPermisoCliente(clienteId, usuario, "subir documentacion de este interesado");
        if (!clienteInteresadoRepository.existsByClienteIdAndInteresadoIdAndHabitualTrue(clienteId, interesadoId)) {
            throw new AccesoDenegadoException("El interesado no pertenece a la cartera habitual del cliente");
        }

        try {
            com.example.gestor_documental.model.Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
            com.example.gestor_documental.model.Interesado interesado = interesadoRepository.findById(interesadoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
            Documento documento = construirDocumentoBase(archivo, tipoDocumento, usuario);
            documento.setCliente(cliente);
            documento.setInteresado(interesado);
            documentoRepository.save(documento);
            return documento;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    private void marcarIncidenciaEnRevisionSiProcede(Expediente expediente, Usuario usuario) {
        if (expediente.getEstadoExpediente() != EstadoExpediente.INCIDENCIA
                && expediente.getEstadoExpediente() != EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return;
        }
        expediente.setEstadoExpediente(EstadoExpediente.REVISANDO_INCIDENCIAS);
        expedienteService.guardar(expediente);
        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                "REVISION INCIDENCIA",
                "Se aporto documentacion para revisar la incidencia."
        );
    }

    /**
     * Similar a guardarParaExpediente, pero asociado a una Solicitud abierta.
     * Detectará automáticamente archivos compuestos y los fragmentará usando el OCR
     * si se designa
     * el tipo especial EXPEDIENTE_COMPLETO como bandera.
     */
    @Override
    public void guardarParaSolicitud(Long solicitudId, MultipartFile archivo, TipoDocumento tipoDocumento,
            Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return;
        }

        try {
            Solicitud solicitud = solicitudRepository.findById(solicitudId)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

            if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para subir documentos a este expediente");
            }
            if (TipoDocumento.EXPEDIENTE_COMPLETO.equals(tipoDocumento)) {
                Documento docOriginal = guardarExpedienteCompletoOriginalParaSolicitud(solicitudId, archivo, usuario);
                procesarExpedienteCompletoSolicitudDocumento(docOriginal.getId(), usuario);
                return;
            }

            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setSolicitud(solicitud);

            documentoRepository.save(doc);
            registrarCargaDocumentoSolicitud(solicitud, doc, usuario);


        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    public List<Documento> listarPorExpediente(Long id) {
        return documentoRepository.findByExpedienteId(id);

    }

    @Override
    public List<Documento> listarPorCliente(Long id) {
        return documentoRepository.findByClienteIdOrderByFechaSubidaDesc(id);
    }

    @Override
    public List<Documento> listarPorInteresadoHabitual(Long clienteId, Long interesadoId) {
        return documentoRepository.findByClienteIdAndInteresadoIdOrderByFechaSubidaDesc(clienteId, interesadoId);
    }

    @Override
    public List<Documento> listarPorSolicitud(Long id) {
        return documentoRepository.findBySolicitudId(id);
    }

    private Documento guardarDocumentoGeneradoParaSolicitud(Solicitud solicitud,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal) throws IOException {
        return guardarDocumentoGeneradoParaSolicitud(solicitud, contenido, tipoDocumento, usuario, nombreArchivoOriginal, true);
    }

    private Documento guardarDocumentoGeneradoParaSolicitud(Solicitud solicitud,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal,
            boolean registrarHistorial) throws IOException {
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setSolicitud(solicitud);
        documentoRepository.save(doc);
        if (registrarHistorial) {
            registrarCargaDocumentoSolicitud(solicitud, doc, usuario);
        }
        return doc;
    }

    private Documento guardarDocumentoGeneradoParaExpediente(Expediente expediente,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal) throws IOException {
        return guardarDocumentoGeneradoParaExpediente(expediente, contenido, tipoDocumento, usuario, nombreArchivoOriginal, null, true);
    }

    private Documento guardarDocumentoGeneradoParaExpediente(Expediente expediente,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal,
            OperacionExpediente operacion,
            boolean registrarHistorial) throws IOException {
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setExpediente(expediente);
        doc.setOperacion(operacion);
        documentoRepository.save(doc);
        if (registrarHistorial) {
            registrarCargaDocumentoExpediente(expediente, doc, usuario);
        }
        return doc;
    }

    private Documento construirDocumentoBase(MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario)
            throws IOException {
        String nombreOriginal = sanitizarNombreArchivo(TextNormalizer.upperOrNull(archivo.getOriginalFilename()));
        validarExtensionPermitida(nombreOriginal);

        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new OperacionInvalidaException("El archivo no tiene nombre válido");
        }

        String nombreUnico = UUID.randomUUID() + "_" + nombreOriginal;

        Path carpetaUploads = obtenerCarpetaUploads();
        Files.createDirectories(carpetaUploads);

        Path rutaArchivo = carpetaUploads.resolve(nombreUnico).normalize();
        if (!rutaArchivo.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        Files.copy(archivo.getInputStream(), rutaArchivo, StandardCopyOption.REPLACE_EXISTING);

        Documento doc = new Documento();
        doc.setNombreArchivoOriginal(nombreOriginal);
        doc.setNombreArchivo(nombreUnico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setSubidoPor(usuario);

        return doc;
    }

    private Documento construirDocumentoBase(byte[] contenido,
            String nombreArchivoOriginal,
            TipoDocumento tipoDocumento,
            Usuario usuario) throws IOException {
        if (nombreArchivoOriginal == null || nombreArchivoOriginal.isBlank()) {
            throw new OperacionInvalidaException("El archivo no tiene nombre válido");
        }

        nombreArchivoOriginal = sanitizarNombreArchivo(TextNormalizer.upperOrNull(nombreArchivoOriginal));
        validarExtensionPermitida(nombreArchivoOriginal);

        String nombreUnico = UUID.randomUUID() + "_" + nombreArchivoOriginal;

        Path carpetaUploads = obtenerCarpetaUploads();
        Files.createDirectories(carpetaUploads);

        Path rutaArchivo = carpetaUploads.resolve(nombreUnico).normalize();
        if (!rutaArchivo.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        Files.write(rutaArchivo, contenido);

        Documento doc = new Documento();
        doc.setNombreArchivoOriginal(nombreArchivoOriginal);
        doc.setNombreArchivo(nombreUnico);
        doc.setTipoDocumento(tipoDocumento);
        doc.setSubidoPor(usuario);

        return doc;
    }

    @Override
    public Optional<Documento> buscarPorId(Long id) {
        return documentoRepository.findById(id);
    }

    @Override
    public Long eliminar(Long id) {
        Documento documento = documentoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));

        Long entidadId = documento.getExpediente() != null
                ? documento.getExpediente().getId()
                : documento.getSolicitud() != null ? documento.getSolicitud().getId() : documento.getCliente().getId();

        Path rutaArchivo = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();

        try {
            Path carpetaUploads = obtenerCarpetaUploads();
            if (!rutaArchivo.startsWith(carpetaUploads)) {
                throw new OperacionInvalidaException("Ruta de archivo no permitida");
            }
            if (Files.exists(rutaArchivo)) {
                Files.delete(rutaArchivo);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al borrar el archivo físico", e);
        }

        desvincularRequisitos(documento);
        documentoRepository.delete(documento);

        return entidadId;
    }

    private void desvincularRequisitos(Documento documento) {
        for (RequisitoDocumentalExpediente requisito : requisitoDocumentalRepository.findByDocumentoId(documento.getId())) {
            requisito.setDocumento(null);
            requisito.setEstado(EstadoRequisitoDocumental.REQUERIDO);
            requisito.setFechaResolucion(null);
            requisito.setResueltoPor(null);
            requisitoDocumentalRepository.save(requisito);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Documento obtenerDocumentoConPermiso(Long documentoId, Usuario usuario) {
        Documento documento = documentoRepository.findByIdConRelaciones(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));

        if (documento.getExpediente() != null &&
                !expedienteService.tienePermisoExpediente(documento.getExpediente(), usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este documento");
        }

        if (documento.getSolicitud() != null &&
                !solicitudService.tienePermisoSolicitud(documento.getSolicitud(), usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este documento");
        }

        if (documento.getCliente() != null) {
            validarPermisoCliente(documento.getCliente().getId(), usuario, "acceder a este documento");
        }

        return documento;
    }

    @Override
    @Transactional
    public void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Usuario usuario) {
        actualizarDocumento(id, nuevoTipo, nuevoNombre, null, usuario);
    }

    @Override
    @Transactional
    public void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, Usuario usuario) {
        actualizarDocumento(id, nuevoTipo, nuevoNombre, operacionId, false, usuario);
    }

    @Override
    @Transactional
    public void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, boolean nombreAutomatico, Usuario usuario) {
        actualizarDocumento(id, nuevoTipo, nuevoNombre, operacionId, operacionId != null, nombreAutomatico, usuario);
    }

    @Override
    @Transactional
    public void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, boolean actualizarOperacion, boolean nombreAutomatico, Usuario usuario) {
        Documento documento = obtenerDocumentoConPermiso(id, usuario);
        validarAdminSiDocumentoCliente(documento, usuario);
        TipoDocumento tipoAnterior = documento.getTipoDocumento();

        if (nuevoTipo != null) {
            documento.setTipoDocumento(nuevoTipo);
        }
        if (actualizarOperacion && documento.getExpediente() != null) {
            documento.setOperacion(resolverOperacionExpediente(documento.getExpediente(), operacionId));
        }
        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            String nombreSanitizado = sanitizarNombreArchivo(TextNormalizer.upperOrNull(nuevoNombre));
            if (!nombreSanitizado.contains(".")) {
                nombreSanitizado = nombreSanitizado + extensionDe(documento.getNombreArchivoOriginal());
            }
            validarExtensionPermitida(nombreSanitizado);
            documento.setNombreArchivoOriginal(nombreSanitizado);
        } else if (nombreAutomatico || (nuevoTipo != null && nuevoTipo != tipoAnterior)) {
            documento.setNombreArchivoOriginal(generarNombreAutomatico(documento));
        }

        documentoRepository.save(documento);
        registrarCorreccionClasificacion(documento, tipoAnterior, documento.getTipoDocumento(), usuario, "EDICION_TIPO");
    }

    private String extensionDe(String nombreArchivo) {
        if (nombreArchivo == null) {
            return ".pdf";
        }
        int index = nombreArchivo.lastIndexOf('.');
        if (index < 0 || index == nombreArchivo.length() - 1) {
            return ".pdf";
        }
        return nombreArchivo.substring(index);
    }

    private String generarNombreAutomatico(Documento documento) {
        String extension = extensionDe(documento.getNombreArchivoOriginal()).toUpperCase(java.util.Locale.ROOT);
        java.util.List<String> partes = new java.util.ArrayList<>();
        if (documento.getExpediente() != null && documento.getExpediente().getMatricula() != null
                && !documento.getExpediente().getMatricula().isBlank()) {
            partes.add(documento.getExpediente().getMatricula());
        } else if (documento.getSolicitud() != null && documento.getSolicitud().getMatricula() != null
                && !documento.getSolicitud().getMatricula().isBlank()) {
            partes.add(documento.getSolicitud().getMatricula());
        } else if (documento.getIncidencia() != null) {
            partes.add("INCIDENCIA " + documento.getIncidencia().getId());
        }
        partes.add(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name().replace('_', ' ') : "DOCUMENTO");
        if (documento.getOperacion() != null && documento.getOperacion().getTipo() != null) {
            partes.add(documento.getOperacion().getTipo().name().replace('_', ' '));
        }

        return sanitizarNombreArchivo(String.join(" - ", partes) + extension);
    }

    private String generarNombreAutomatico(Documento documentoOriginal, TipoDocumento nuevoTipo, OperacionExpediente operacion) {
        Documento documento = new Documento();
        documento.setNombreArchivoOriginal(documentoOriginal.getNombreArchivoOriginal());
        documento.setTipoDocumento(nuevoTipo != null ? nuevoTipo : documentoOriginal.getTipoDocumento());
        documento.setExpediente(documentoOriginal.getExpediente());
        documento.setSolicitud(documentoOriginal.getSolicitud());
        documento.setIncidencia(documentoOriginal.getIncidencia());
        documento.setOperacion(operacion);
        return generarNombreAutomatico(documento);
    }

    private String asegurarExtension(String nombreArchivo, String nombreReferencia) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            return "documento_extraido" + extensionDe(nombreReferencia);
        }
        String nombreSanitizado = sanitizarNombreArchivo(TextNormalizer.upperOrNull(nombreArchivo));
        if (!nombreSanitizado.contains(".")) {
            nombreSanitizado = nombreSanitizado + extensionDe(nombreReferencia);
        }
        return nombreSanitizado;
    }

    @Override
    @Transactional(readOnly = true)
    public int contarPaginasDocumento(Long id, Usuario usuario) {
        Documento documento = obtenerDocumentoConPermiso(id, usuario);
        Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();

        try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.pdmodel.PDDocument.load(Files.readAllBytes(ruta))) {
            return pdfDoc.getNumberOfPages();
        } catch (IOException e) {
            throw new RuntimeException("Error al contar paginas del documento", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderizarPaginaDocumento(Long id, int pagina, Usuario usuario) {
        Documento documento = obtenerDocumentoConPermiso(id, usuario);
        Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();

        try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.pdmodel.PDDocument.load(Files.readAllBytes(ruta));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (pagina < 1 || pagina > pdfDoc.getNumberOfPages()) {
                throw new OperacionInvalidaException("Pagina fuera de rango");
            }
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(pdfDoc);
            java.awt.image.BufferedImage image = renderer.renderImageWithDPI(pagina - 1, 105, org.apache.pdfbox.rendering.ImageType.RGB);
            javax.imageio.ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al renderizar pagina del documento", e);
        }
    }

    /**
     * DANGER (Operación Mutativa): Fracciona un PDF cortando páginas desde un
     * documento maestro hacia uno nuevo.
     * Regla estricta: Esta acción es "Destructiva" para el original. El archivo
     * original pierde físicamente
     * las páginas extraídas para evitar duplicidades de información en la Gestoría.
     * Usa pdfSplitService para reescribir in situ el PDF origen sin el rango
     * extraído.
     */
    @Override
    @Transactional
    public void extraerPaginasDocumento(Long idOriginal, String rangoPaginas, TipoDocumento nuevoTipo,
            String nuevoNombre, Usuario usuario) {
        extraerPaginasDocumento(idOriginal, rangoPaginas, nuevoTipo, nuevoNombre, null, usuario);
    }

    @Override
    @Transactional
    public void extraerPaginasDocumento(Long idOriginal, String rangoPaginas, TipoDocumento nuevoTipo,
            String nuevoNombre, Long operacionId, Usuario usuario) {
        Documento documentoOriginal = obtenerDocumentoConPermiso(idOriginal, usuario);
        validarTransformacionExpedienteOSolicitud(documentoOriginal);

        Path rutaOriginal = obtenerCarpetaUploads().resolve(documentoOriginal.getNombreArchivo()).normalize();

        try {
            if (!Files.exists(rutaOriginal)) {
                throw new RuntimeException("El archivo físico del documento no existe.");
            }

            byte[] bytesOriginales = Files.readAllBytes(rutaOriginal);

            // Total number of pages
            int totalPaginas = 0;
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.pdmodel.PDDocument
                    .load(bytesOriginales)) {
                totalPaginas = pdfDoc.getNumberOfPages();
            }

            List<Integer> paginasExtraer = pdfSplitService.parseRangoPaginas(rangoPaginas, totalPaginas);

            if (paginasExtraer.isEmpty()) {
                throw new OperacionInvalidaException("Rango de páginas inválido o vacío.");
            }

            if (paginasExtraer.size() >= totalPaginas) {
                throw new OperacionInvalidaException("No se pueden extraer todas las paginas del documento");
            }

            byte[] pdfExtraido = pdfSplitService.extraerPaginas(bytesOriginales, paginasExtraer);
            byte[] pdfRestante = pdfSplitService.eliminarPaginas(bytesOriginales, paginasExtraer);

            // Sobrescribir el archivo original para que no tenga las páginas extraídas
            // (evita duplicidad)
            if (documentoOriginal.getExpediente() != null) {
                OperacionExpediente operacion = resolverOperacionExpediente(documentoOriginal.getExpediente(), operacionId);
                String nombreNuevo = nuevoNombre != null && !nuevoNombre.isBlank()
                        ? asegurarExtension(nuevoNombre, documentoOriginal.getNombreArchivoOriginal())
                        : generarNombreAutomatico(documentoOriginal, nuevoTipo, operacion);
                Documento generado = guardarDocumentoGeneradoParaExpediente(
                        documentoOriginal.getExpediente(), pdfExtraido, nuevoTipo, usuario, nombreNuevo, operacion, false);
                registrarCorreccionClasificacion(generado, documentoOriginal.getTipoDocumento(), nuevoTipo, usuario, "EXTRACCION_PAGINAS");
            } else if (documentoOriginal.getSolicitud() != null) {
                String nombreNuevo = nuevoNombre != null && !nuevoNombre.isBlank()
                        ? asegurarExtension(nuevoNombre, documentoOriginal.getNombreArchivoOriginal())
                        : generarNombreAutomatico(documentoOriginal, nuevoTipo, null);
                Documento generado = guardarDocumentoGeneradoParaSolicitud(
                        documentoOriginal.getSolicitud(), pdfExtraido, nuevoTipo, usuario, nombreNuevo, false);
                registrarCorreccionClasificacion(generado, documentoOriginal.getTipoDocumento(), nuevoTipo, usuario, "EXTRACCION_PAGINAS");
            }

            Files.write(rutaOriginal, pdfRestante);

        } catch (IOException e) {
            throw new RuntimeException("Error al leer o sobrescribir el archivo original físico al extraer páginas.",
                    e);
        }
    }

    @Override
    @Transactional
    public void eliminarPaginasDocumento(Long id, String rangoPaginas, Usuario usuario) {
        Documento documento = obtenerDocumentoConPermiso(id, usuario);
        validarTransformacionExpedienteOSolicitud(documento);
        Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();

        try {
            if (!Files.exists(ruta)) {
                throw new RecursoNoEncontradoException("El archivo fisico del documento no existe");
            }

            byte[] bytesOriginales = Files.readAllBytes(ruta);
            int totalPaginas;
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.pdmodel.PDDocument.load(bytesOriginales)) {
                totalPaginas = pdfDoc.getNumberOfPages();
            }

            List<Integer> paginasEliminar = pdfSplitService.parseRangoPaginas(rangoPaginas, totalPaginas);
            if (paginasEliminar.isEmpty()) {
                throw new OperacionInvalidaException("Rango de paginas invalido o vacio");
            }
            if (paginasEliminar.size() >= totalPaginas) {
                throw new OperacionInvalidaException("No se pueden eliminar todas las paginas del documento");
            }

            Files.write(ruta, pdfSplitService.eliminarPaginas(bytesOriginales, paginasEliminar));
        } catch (IOException e) {
            throw new RuntimeException("Error al eliminar paginas del documento", e);
        }
    }

    @Override
    @Transactional
    public void unirDocumentos(Long documentoPrincipalId, List<Long> documentoIds, TipoDocumento tipoDocumento, String nombreArchivo, Usuario usuario) {
        unirDocumentos(documentoPrincipalId, documentoIds, tipoDocumento, nombreArchivo, null, usuario);
    }

    @Override
    @Transactional
    public void unirDocumentos(Long documentoPrincipalId, List<Long> documentoIds, TipoDocumento tipoDocumento, String nombreArchivo, Long operacionId, Usuario usuario) {
        Documento principal = obtenerDocumentoConPermiso(documentoPrincipalId, usuario);
        validarTransformacionExpedienteOSolicitud(principal);
        TipoDocumento tipoAnterior = principal.getTipoDocumento();
        List<Long> ids = documentoIds == null ? List.of() : documentoIds.stream()
                .filter(id -> id != null && !id.equals(documentoPrincipalId))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona al menos otro documento para unir");
        }

        try {
            List<Documento> documentos = new java.util.ArrayList<>();
            documentos.add(principal);
            for (Long id : ids) {
                Documento documento = obtenerDocumentoConPermiso(id, usuario);
                validarMismoContenedor(principal, documento);
                documentos.add(documento);
            }

            List<byte[]> contenidos = new java.util.ArrayList<>();
            for (Documento documento : documentos) {
                Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();
                if (!Files.exists(ruta)) {
                    throw new RecursoNoEncontradoException("El archivo fisico del documento no existe");
                }
                contenidos.add(Files.readAllBytes(ruta));
            }

            Path rutaPrincipal = obtenerCarpetaUploads().resolve(principal.getNombreArchivo()).normalize();
            Files.write(rutaPrincipal, pdfSplitService.unirDocumentos(contenidos));
            eliminarLecturasDocumento(principal);

            if (tipoDocumento != null) {
                principal.setTipoDocumento(tipoDocumento);
            }
            if (nombreArchivo != null && !nombreArchivo.isBlank()) {
                String nombreSanitizado = sanitizarNombreArchivo(TextNormalizer.upperOrNull(nombreArchivo));
                if (!nombreSanitizado.contains(".")) {
                    nombreSanitizado = nombreSanitizado + extensionDe(principal.getNombreArchivoOriginal());
                }
                validarExtensionPermitida(nombreSanitizado);
                principal.setNombreArchivoOriginal(nombreSanitizado);
            }
            if (principal.getExpediente() != null) {
                principal.setOperacion(resolverOperacionExpediente(principal.getExpediente(), operacionId));
            }
            documentoRepository.save(principal);
            registrarCorreccionClasificacion(principal, tipoAnterior, principal.getTipoDocumento(), usuario, "UNION_DOCUMENTOS");

            for (Documento documento : documentos.subList(1, documentos.size())) {
                reasignarRequisitos(documento, principal);
                eliminarDocumentoFusionado(documento);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al unir documentos", e);
        }
    }

    private void validarMismoContenedor(Documento principal, Documento documento) {
        Long expedientePrincipal = principal.getExpediente() != null ? principal.getExpediente().getId() : null;
        Long expedienteDocumento = documento.getExpediente() != null ? documento.getExpediente().getId() : null;
        Long solicitudPrincipal = principal.getSolicitud() != null ? principal.getSolicitud().getId() : null;
        Long solicitudDocumento = documento.getSolicitud() != null ? documento.getSolicitud().getId() : null;
        Long clientePrincipal = principal.getCliente() != null ? principal.getCliente().getId() : null;
        Long clienteDocumento = documento.getCliente() != null ? documento.getCliente().getId() : null;

        if (!java.util.Objects.equals(expedientePrincipal, expedienteDocumento)
                || !java.util.Objects.equals(solicitudPrincipal, solicitudDocumento)
                || !java.util.Objects.equals(clientePrincipal, clienteDocumento)) {
            throw new OperacionInvalidaException("Solo se pueden unir documentos del mismo expediente o solicitud");
        }
    }

    @Override
    @Transactional
    public Documento guardarExpedienteCompletoOriginalParaSolicitud(Long solicitudId, MultipartFile archivo, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        validarExpedienteCompletoPdf(archivo.getOriginalFilename());

        try {
            Solicitud solicitud = solicitudRepository.findById(solicitudId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
            if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para subir documentos a esta solicitud");
            }

            Documento docOriginal = construirDocumentoBase(archivo, TipoDocumento.EXPEDIENTE_COMPLETO, usuario);
            docOriginal.setSolicitud(solicitud);
            documentoRepository.save(docOriginal);
            return docOriginal;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el expediente completo", e);
        }
    }

    @Override
    @Transactional
    public int procesarExpedienteCompletoSolicitudDocumento(Long documentoId, Usuario usuario) {
        Documento docOriginal = obtenerDocumentoConPermiso(documentoId, usuario);
        if (docOriginal.getSolicitud() == null) {
            throw new OperacionInvalidaException("El documento no pertenece a una solicitud");
        }
        if (docOriginal.getTipoDocumento() != TipoDocumento.EXPEDIENTE_COMPLETO) {
            throw new OperacionInvalidaException("El documento no es un expediente completo");
        }
        validarExpedienteCompletoPdf(docOriginal.getNombreArchivoOriginal());

        Path rutaOriginal = obtenerCarpetaUploads().resolve(docOriginal.getNombreArchivo()).normalize();
        Path carpetaUploads = obtenerCarpetaUploads();
        if (!rutaOriginal.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        try {
            if (!Files.exists(rutaOriginal)) {
                throw new RecursoNoEncontradoException("El archivo fisico del documento no existe");
            }

            byte[] pdfOriginal = Files.readAllBytes(rutaOriginal);
            log.info("OCR_DIAG separacion-inicio tipo=SOLICITUD documentoId={} solicitudId={} archivo={} bytes={}",
                    docOriginal.getId(),
                    docOriginal.getSolicitud().getId(),
                    docOriginal.getNombreArchivoOriginal(),
                    pdfOriginal.length);
            MultipartFile archivoProcesable = new PathMultipartFile(
                    rutaOriginal,
                    docOriginal.getNombreArchivoOriginal(),
                    "application/pdf"
            );
            List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivoProcesable);
            log.info("OCR_DIAG separacion-detectados tipo=SOLICITUD documentoId={} solicitudId={} bloques={}",
                    docOriginal.getId(),
                    docOriginal.getSolicitud().getId(),
                    resumenDetectados(documentosDetectados));

            if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                log.warn("OCR_DIAG separacion-sin-detectados tipo=SOLICITUD documentoId={} solicitudId={}",
                        docOriginal.getId(),
                        docOriginal.getSolicitud().getId());
                registrarProcesamientoExpedienteCompleto(docOriginal.getSolicitud(), docOriginal, usuario, 0);
                return 0;
            }

            int generados = 0;
            for (DocumentoDetectadoDto detectado : documentosDetectados) {
                byte[] pdfSeparado = pdfSplitService.extraerPaginas(pdfOriginal, detectado.getPaginas());
                Documento generado = guardarDocumentoGeneradoParaSolicitud(
                        docOriginal.getSolicitud(),
                        pdfSeparado,
                        detectado.getTipoDocumento(),
                        usuario,
                        docOriginal.getSolicitud().getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf",
                        false);
                log.info("OCR_DIAG separacion-generado tipo=SOLICITUD originalId={} generadoId={} solicitudId={} tipoDocumento={} paginas={} bytes={}",
                        docOriginal.getId(),
                        generado.getId(),
                        docOriginal.getSolicitud().getId(),
                        detectado.getTipoDocumento(),
                        paginasHumanas(detectado.getPaginas()),
                        pdfSeparado.length);
                generados++;
            }
            registrarProcesamientoExpedienteCompleto(docOriginal.getSolicitud(), docOriginal, usuario, generados);
            return generados;
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el expediente completo", e);
        }
    }

    private void validarAdminSiDocumentoCliente(Documento documento, Usuario usuario) {
        if (documento.getCliente() != null && documento.getInteresado() == null
                && (usuario == null || usuario.getRolUsuario() != RolUsuario.ADMIN)) {
            throw new AccesoDenegadoException("Solo un administrador puede modificar documentos del cliente");
        }
    }

    private void validarTransformacionExpedienteOSolicitud(Documento documento) {
        if (documento.getCliente() != null) {
            throw new OperacionInvalidaException("Los documentos del cliente no admiten esta operacion");
        }
    }

    private void validarPermisoCliente(Long clienteId, Usuario usuario, String accion) {
        boolean admin = usuario != null && usuario.getRolUsuario() == RolUsuario.ADMIN;
        boolean clientePropio = usuario != null
                && usuario.getCliente() != null
                && usuario.getCliente().getId().equals(clienteId);
        if (!admin && !clientePropio) {
            throw new AccesoDenegadoException("No tienes permiso para " + accion);
        }
    }

    private OperacionExpediente resolverOperacionExpediente(Expediente expediente, Long operacionId) {
        if (operacionId == null) {
            return null;
        }
        OperacionExpediente operacion = operacionExpedienteRepository.findById(operacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operacion de expediente no encontrada"));
        if (operacion.getExpediente() == null || expediente == null
                || !operacion.getExpediente().getId().equals(expediente.getId())) {
            throw new OperacionInvalidaException("La operacion no pertenece a este expediente");
        }
        return operacion;
    }

    private void reasignarRequisitos(Documento origen, Documento destino) {
        for (RequisitoDocumentalExpediente requisito : requisitoDocumentalRepository.findByDocumentoId(origen.getId())) {
            requisito.setDocumento(destino);
            requisito.setEstado(EstadoRequisitoDocumental.APORTADO);
            requisitoDocumentalRepository.save(requisito);
        }
    }

    private void eliminarDocumentoFusionado(Documento documento) throws IOException {
        eliminarLecturasDocumento(documento);
        Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();
        Path carpetaUploads = obtenerCarpetaUploads();
        if (!ruta.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        Files.deleteIfExists(ruta);
        documentoRepository.delete(documento);
    }

    private void eliminarLecturasDocumento(Documento documento) {
        if (documento == null || documento.getId() == null) {
            return;
        }
        documentoIdentidadLecturaRepository.deleteByDocumentoId(documento.getId());
        documentoRolesLecturaRepository.deleteByDocumentoId(documento.getId());
        documentoVehiculoLecturaRepository.deleteByDocumentoId(documento.getId());
    }

    private Path obtenerCarpetaUploads() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private String resumenDetectados(List<DocumentoDetectadoDto> documentosDetectados) {
        if (documentosDetectados == null || documentosDetectados.isEmpty()) {
            return "[]";
        }
        return documentosDetectados.stream()
                .map(documento -> documento.getTipoDocumento() + ":" + paginasHumanas(documento.getPaginas()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String paginasHumanas(List<Integer> paginas) {
        if (paginas == null || paginas.isEmpty()) {
            return "[]";
        }
        return paginas.stream()
                .map(pagina -> String.valueOf(pagina + 1))
                .collect(Collectors.joining("-", "[", "]"));
    }

    private record PathMultipartFile(Path path, String originalFilename, String contentType) implements MultipartFile {
        @Override
        public String getName() {
            return "archivo";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void registrarCargaDocumentoExpediente(Expediente expediente, Documento documento, Usuario usuario) {
        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                ACCION_CARGAR_DOCUMENTO,
                "Se cargó el documento: " + documento.getNombreArchivoOriginal());
    }

    private void registrarCargaDocumentoSolicitud(Solicitud solicitud, Documento documento, Usuario usuario) {
        historialCambioService.registrarCambioSolicitud(
                solicitud,
                usuario,
                ACCION_CARGAR_DOCUMENTO,
                "Se cargó el documento: " + documento.getNombreArchivoOriginal());
    }

    private void registrarCorreccionClasificacion(
            Documento documento,
            TipoDocumento tipoAnterior,
            TipoDocumento tipoCorregido,
            Usuario usuario,
            String origen
    ) {
        if (documento == null || tipoCorregido == null || tipoCorregido == tipoAnterior) {
            return;
        }
        CorreccionClasificacionDocumento correccion = new CorreccionClasificacionDocumento();
        correccion.setDocumento(documento);
        correccion.setExpediente(documento.getExpediente());
        correccion.setSolicitud(documento.getSolicitud());
        correccion.setUsuario(usuario);
        correccion.setTipoAnterior(tipoAnterior);
        correccion.setTipoCorregido(tipoCorregido);
        correccion.setOrigen(origen);
        correccion.setNombreArchivoOriginal(documento.getNombreArchivoOriginal());
        correccionClasificacionDocumentoRepository.save(correccion);
    }

    private void registrarProcesamientoExpedienteCompleto(Expediente expediente, Documento documento, Usuario usuario, int documentosDetectados) {
        String detalle = documentosDetectados > 0
                ? "Se subio y proceso el expediente completo '" + documento.getNombreArchivoOriginal()
                        + "'. Documentos separados: " + documentosDetectados + "."
                : "Se subio el expediente completo '" + documento.getNombreArchivoOriginal()
                        + "', pero no se detectaron documentos para separar.";
        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                ACCION_CARGAR_DOCUMENTO,
                detalle);
    }

    private void registrarProcesamientoExpedienteCompleto(Solicitud solicitud, Documento documento, Usuario usuario, int documentosDetectados) {
        String detalle = documentosDetectados > 0
                ? "Se subio y proceso el expediente completo '" + documento.getNombreArchivoOriginal()
                        + "'. Documentos separados: " + documentosDetectados + "."
                : "Se subio el expediente completo '" + documento.getNombreArchivoOriginal()
                        + "', pero no se detectaron documentos para separar.";
        historialCambioService.registrarCambioSolicitud(
                solicitud,
                usuario,
                ACCION_CARGAR_DOCUMENTO,
                detalle);
    }

    private String sanitizarNombreArchivo(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            throw new OperacionInvalidaException("El archivo no tiene nombre valido");
        }

        String nombre = Paths.get(nombreArchivo).getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");

        if (nombre.isBlank() || ".".equals(nombre) || "..".equals(nombre)) {
            throw new OperacionInvalidaException("El archivo no tiene nombre valido");
        }

        if (nombre.length() > 180) {
            int punto = nombre.lastIndexOf('.');
            String extension = punto >= 0 ? nombre.substring(punto) : "";
            String base = punto >= 0 ? nombre.substring(0, punto) : nombre;
            int maxBase = Math.max(1, 180 - extension.length());
            nombre = base.substring(0, Math.min(base.length(), maxBase)) + extension;
        }

        return nombre;
    }

    private void validarExtensionPermitida(String nombreArchivo) {
        String extension = obtenerExtension(nombreArchivo);
        List<String> permitidas = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(ext -> !ext.isBlank())
                .collect(Collectors.toList());

        if (extension.isBlank() || !permitidas.contains(extension)) {
            throw new OperacionInvalidaException("Tipo de archivo no permitido");
        }
    }

    private void validarExpedienteCompletoPdf(String nombreArchivo) {
        if (!"pdf".equals(obtenerExtension(nombreArchivo != null ? nombreArchivo : ""))) {
            throw new OperacionInvalidaException("El expediente completo debe subirse en formato PDF");
        }
    }

    private String obtenerExtension(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto < 0 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase();
    }

}
