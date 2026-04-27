package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final DocumentoRepository documentoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
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
    public void guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento,
            Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return;
        }

        try {
            Expediente expediente = expedienteRepository.findById(expedienteId)
                    .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

            if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para subir documentos a este expediente");
            }

            if (TipoDocumento.EXPEDIENTE_COMPLETO.equals(tipoDocumento)) {
                // SIEMPRE guardar el original primero
                Documento docOriginal = construirDocumentoBase(archivo, tipoDocumento, usuario);
                docOriginal.setExpediente(expediente);
                documentoRepository.save(docOriginal);


                List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivo);

                if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                    return;
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
                            expediente.getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf");
                }
                return;
            }
            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setExpediente(expediente);

            documentoRepository.save(doc);


        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
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
                            solicitud.getMatricula() + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf");
                }
                return;
            }

            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setSolicitud(solicitud);

            documentoRepository.save(doc);


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
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setSolicitud(solicitud);
        documentoRepository.save(doc);


    }

    private void guardarDocumentoGeneradoParaExpediente(Expediente expediente,
            byte[] contenido,
            TipoDocumento tipoDocumento,
            Usuario usuario,
            String nombreArchivoOriginal) throws IOException {
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setExpediente(expediente);
        documentoRepository.save(doc);


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

        documentoRepository.delete(documento);

        return entidadId;
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
        Documento documento = obtenerDocumentoConPermiso(id, usuario);

        if (nuevoTipo != null) {
            documento.setTipoDocumento(nuevoTipo);
        }
        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            String nombreSanitizado = sanitizarNombreArchivo(nuevoNombre);
            validarExtensionPermitida(nombreSanitizado);
            documento.setNombreArchivoOriginal(nombreSanitizado);
        }

        documentoRepository.save(documento);

        if (documento.getExpediente() != null) {
            historialCambioService.registrarCambioExpediente(
                    documento.getExpediente(), usuario, "ACTUALIZAR DOCUMENTO",
                    "Se actualizó el documento: " + documento.getNombreArchivoOriginal());
        } else if (documento.getSolicitud() != null) {
            historialCambioService.registrarCambioSolicitud(
                    documento.getSolicitud(), usuario, "ACTUALIZAR DOCUMENTO",
                    "Se actualizó el documento: " + documento.getNombreArchivoOriginal());
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

            byte[] pdfExtraido = pdfSplitService.extraerPaginas(bytesOriginales, paginasExtraer);
            byte[] pdfRestante = pdfSplitService.eliminarPaginas(bytesOriginales, paginasExtraer);

            // Sobrescribir el archivo original para que no tenga las páginas extraídas
            // (evita duplicidad)
            Files.write(rutaOriginal, pdfRestante);

            if (documentoOriginal.getExpediente() != null) {
                guardarDocumentoGeneradoParaExpediente(
                        documentoOriginal.getExpediente(), pdfExtraido, nuevoTipo, usuario, nuevoNombre);
            } else if (documentoOriginal.getSolicitud() != null) {
                guardarDocumentoGeneradoParaSolicitud(
                        documentoOriginal.getSolicitud(), pdfExtraido, nuevoTipo, usuario, nuevoNombre);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error al leer o sobrescribir el archivo original físico al extraer páginas.",
                    e);
        }
    }


    private Path obtenerCarpetaUploads() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
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
