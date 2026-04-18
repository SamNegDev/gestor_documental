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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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


    @Override
    public void guardar(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento) {
        guardarParaExpediente(expedienteId, archivo, tipoDocumento, null);
    }

    @Override
    public void guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return;
        }

        try {
            Expediente expediente = expedienteRepository.findById(expedienteId)
                    .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

            if (TipoDocumento.EXPEDIENTE_COMPLETO.equals(tipoDocumento)) {
                List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivo);

                if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                    Documento doc = construirDocumentoBase(archivo, TipoDocumento.OTROS, usuario);
                    doc.setExpediente(expediente);
                    documentoRepository.save(doc);
                    
                    historialCambioService.registrarCambioExpediente(
                            expediente, 
                            usuario, 
                            "NUEVO DOCUMENTO", 
                            "Se subió el documento: " + doc.getNombreArchivoOriginal()
                    );
                    return;
                }

                byte[] pdfOriginal = archivo.getBytes();

                int indice = 1;
                for (DocumentoDetectadoDto detectado : documentosDetectados) {
                    byte[] pdfSeparado = pdfSplitService.extraerPaginas(
                            pdfOriginal,
                            detectado.getPaginas()
                    );

                    guardarDocumentoGeneradoParaExpediente(
                            expediente,
                            pdfSeparado,
                            detectado.getTipoDocumento(),
                            usuario,
                            "ocr_" + indice + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf"
                    );
                    indice++;
                }
                return;
            }
            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setExpediente(expediente);

            documentoRepository.save(doc);
            
            historialCambioService.registrarCambioExpediente(
                    expediente, 
                    usuario, 
                    "NUEVO DOCUMENTO", 
                    "Se subió el documento: " + doc.getNombreArchivoOriginal()
            );

        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    public void guardarParaSolicitud(Long solicitudId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) {
        if (archivo == null || archivo.isEmpty()) {
            return;
        }

        try {
            Solicitud solicitud = solicitudRepository.findById(solicitudId)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

            if (TipoDocumento.EXPEDIENTE_COMPLETO.equals(tipoDocumento)) {
                List<DocumentoDetectadoDto> documentosDetectados = ocrPdfService.detectarDocumentos(archivo);

                if (documentosDetectados == null || documentosDetectados.isEmpty()) {
                    Documento doc = construirDocumentoBase(archivo, TipoDocumento.OTROS, usuario);
                    doc.setSolicitud(solicitud);
                    documentoRepository.save(doc);
                    
                    historialCambioService.registrarCambioSolicitud(
                            solicitud, 
                            usuario, 
                            "NUEVO DOCUMENTO", 
                            "Se subió el documento: " + doc.getNombreArchivoOriginal()
                    );
                    return;
                }

                byte[] pdfOriginal = archivo.getBytes();

                int indice = 1;
                for (DocumentoDetectadoDto detectado : documentosDetectados) {
                    byte[] pdfSeparado = pdfSplitService.extraerPaginas(
                            pdfOriginal,
                            detectado.getPaginas()
                    );

                    guardarDocumentoGeneradoParaSolicitud(
                            solicitud,
                            pdfSeparado,
                            detectado.getTipoDocumento(),
                            usuario,
                            "ocr_" + indice + "_" + detectado.getTipoDocumento().name().toLowerCase() + ".pdf"
                    );
                    indice++;
                }
                return;
            }



            Documento doc = construirDocumentoBase(archivo, tipoDocumento, usuario);
            doc.setSolicitud(solicitud);

            documentoRepository.save(doc);
            
            historialCambioService.registrarCambioSolicitud(
                    solicitud, 
                    usuario, 
                    "NUEVO DOCUMENTO", 
                    "Se subió el documento: " + doc.getNombreArchivoOriginal()
            );

        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    public List<Documento> listarPorExpediente(Long id) {
        System.out.println("DOCS EXPEDIENTE: " + documentoRepository.findByExpedienteId(id));
        return documentoRepository.findByExpedienteId(id);

    }

    private void guardarDocumentoGeneradoParaSolicitud(Solicitud solicitud,
                                                       byte[] contenido,
                                                       TipoDocumento tipoDocumento,
                                                       Usuario usuario,
                                                       String nombreArchivoOriginal) throws IOException {
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setSolicitud(solicitud);
        documentoRepository.save(doc);
        
        historialCambioService.registrarCambioSolicitud(
                solicitud, 
                usuario, 
                "NUEVO DOCUMENTO", 
                "Se generó el documento OCR: " + doc.getNombreArchivoOriginal()
        );
    }
    private void guardarDocumentoGeneradoParaExpediente(Expediente expediente,
                                                       byte[] contenido,
                                                       TipoDocumento tipoDocumento,
                                                       Usuario usuario,
                                                       String nombreArchivoOriginal) throws IOException {
        Documento doc = construirDocumentoBase(contenido, nombreArchivoOriginal, tipoDocumento, usuario);
        doc.setExpediente(expediente);
        documentoRepository.save(doc);
        
        historialCambioService.registrarCambioExpediente(
                expediente, 
                usuario, 
                "NUEVO DOCUMENTO", 
                "Se generó el documento OCR: " + doc.getNombreArchivoOriginal()
        );
    }


    private Documento construirDocumentoBase(MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario) throws IOException {
        String nombreOriginal = archivo.getOriginalFilename();

        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new OperacionInvalidaException("El archivo no tiene nombre válido");
        }

        String nombreUnico = UUID.randomUUID() + "_" + nombreOriginal;

        Path carpetaUploads = Paths.get("uploads");
        Files.createDirectories(carpetaUploads);

        Path rutaArchivo = carpetaUploads.resolve(nombreUnico);
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

        String nombreUnico = UUID.randomUUID() + "_" + nombreArchivoOriginal;

        Path carpetaUploads = Paths.get("uploads");
        Files.createDirectories(carpetaUploads);

        Path rutaArchivo = carpetaUploads.resolve(nombreUnico);
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
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));

        Long entidadId = documento.getExpediente() != null
                ? documento.getExpediente().getId()
                : documento.getSolicitud().getId();

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

        try {
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
    public Documento obtenerDocumentoConPermiso(Long documentoId, Usuario usuario) {
        Documento documento = documentoRepository.findById(documentoId)
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

}