package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
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

    private static final String ACCION_CARGAR_DOCUMENTO = "CARGAR DOCUMENTO";

    private final DocumentoRepository documentoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final SolicitudRepository solicitudRepository;
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
                // SIEMPRE guardar el original primero
                Documento docOriginal = construirDocumentoBase(archivo, tipoDocumento, usuario);
                docOriginal.setExpediente(expediente);
                docOriginal.setOperacion(operacion);
                documentoRepository.save(docOriginal);


                List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivo);

                if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                    registrarProcesamientoExpedienteCompleto(expediente, docOriginal, usuario, 0);
                    return docOriginal;
                }

                byte[] pdfOriginal = archivo.getBytes();

                for (DocumentoDetectadoDto detectado : documentosDetectados) {
                    byte[] pdfSeparado = pdfSplitService.extraerPaginas(
                            pdfOriginal,
                            detectado.getPaginas());

                    guardarDocumentoGeneradoParaExpediente(
                            expediente,
                            pdfSeparado,
                            detectado.getTipoDocumento(),
                            usuario,
                            expediente.getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf",
                            null,
                            false);
                }
                registrarProcesamientoExpedienteCompleto(expediente, docOriginal, usuario, documentosDetectados.size());
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

    private void marcarIncidenciaEnRevisionSiProcede(Expediente expediente, Usuario usuario) {
        if (expediente.getEstadoExpediente() != EstadoExpediente.INCIDENCIA) {
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
                // SIEMPRE guardar el original primero
                Documento docOriginal = construirDocumentoBase(archivo, tipoDocumento, usuario);
                docOriginal.setSolicitud(solicitud);
                documentoRepository.save(docOriginal);


                List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivo);

                if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                    registrarProcesamientoExpedienteCompleto(solicitud, docOriginal, usuario, 0);
                    return;
                }

                byte[] pdfOriginal = archivo.getBytes();

                for (DocumentoDetectadoDto detectado : documentosDetectados) {
                    byte[] pdfSeparado = pdfSplitService.extraerPaginas(
                            pdfOriginal,
                            detectado.getPaginas());

                    guardarDocumentoGeneradoParaSolicitud(
                            solicitud,
                            pdfSeparado,
                            detectado.getTipoDocumento(),
                            usuario,
                            solicitud.getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf",
                            false);
                }
                registrarProcesamientoExpedienteCompleto(solicitud, docOriginal, usuario, documentosDetectados.size());
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
    public List<Documento> listarPorSolicitud(Long id) {
        return documentoRepository.findBySolicitudId(id);
    }

    private void guardarDocumentoGeneradoParaSolicitud(Solicitud solicitud,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal) throws IOException {
        guardarDocumentoGeneradoParaSolicitud(solicitud, contenido, tipoDocumento, usuario, nombreArchivoOriginal, true);
    }

    private void guardarDocumentoGeneradoParaSolicitud(Solicitud solicitud,
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


    }

    private void guardarDocumentoGeneradoParaExpediente(Expediente expediente,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal) throws IOException {
        guardarDocumentoGeneradoParaExpediente(expediente, contenido, tipoDocumento, usuario, nombreArchivoOriginal, null, true);
    }

    private void guardarDocumentoGeneradoParaExpediente(Expediente expediente,
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


    }

    private Documento construirDocumentoBase(MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario)
            throws IOException {
        String nombreOriginal = sanitizarNombreArchivo(archivo.getOriginalFilename());
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

        nombreArchivoOriginal = sanitizarNombreArchivo(nombreArchivoOriginal);
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
                : documento.getSolicitud().getId();

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
        Documento documento = obtenerDocumentoConPermiso(id, usuario);

        if (nuevoTipo != null) {
            documento.setTipoDocumento(nuevoTipo);
        }
        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            String nombreSanitizado = sanitizarNombreArchivo(nuevoNombre);
            if (!nombreSanitizado.contains(".")) {
                nombreSanitizado = nombreSanitizado + extensionDe(documento.getNombreArchivoOriginal());
            }
            validarExtensionPermitida(nombreSanitizado);
            documento.setNombreArchivoOriginal(nombreSanitizado);
        }
        if (documento.getExpediente() != null) {
            documento.setOperacion(resolverOperacionExpediente(documento.getExpediente(), operacionId));
        }

        documentoRepository.save(documento);
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

    private String asegurarExtension(String nombreArchivo, String nombreReferencia) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            return "documento_extraido" + extensionDe(nombreReferencia);
        }
        String nombreSanitizado = sanitizarNombreArchivo(nombreArchivo);
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
            String nombreNuevo = asegurarExtension(nuevoNombre, documentoOriginal.getNombreArchivoOriginal());

            if (documentoOriginal.getExpediente() != null) {
                OperacionExpediente operacion = resolverOperacionExpediente(documentoOriginal.getExpediente(), operacionId);
                guardarDocumentoGeneradoParaExpediente(
                        documentoOriginal.getExpediente(), pdfExtraido, nuevoTipo, usuario, nombreNuevo, operacion, false);
            } else if (documentoOriginal.getSolicitud() != null) {
                guardarDocumentoGeneradoParaSolicitud(
                        documentoOriginal.getSolicitud(), pdfExtraido, nuevoTipo, usuario, nombreNuevo, false);
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

            if (tipoDocumento != null) {
                principal.setTipoDocumento(tipoDocumento);
            }
            if (nombreArchivo != null && !nombreArchivo.isBlank()) {
                String nombreSanitizado = sanitizarNombreArchivo(nombreArchivo);
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

        if (!java.util.Objects.equals(expedientePrincipal, expedienteDocumento)
                || !java.util.Objects.equals(solicitudPrincipal, solicitudDocumento)) {
            throw new OperacionInvalidaException("Solo se pueden unir documentos del mismo expediente o solicitud");
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
        Path ruta = obtenerCarpetaUploads().resolve(documento.getNombreArchivo()).normalize();
        Path carpetaUploads = obtenerCarpetaUploads();
        if (!ruta.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        Files.deleteIfExists(ruta);
        documentoRepository.delete(documento);
    }

    private Path obtenerCarpetaUploads() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
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

    private String obtenerExtension(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto < 0 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase();
    }

}
