package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.service.DocumentoService;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentoServiceImpl implements DocumentoService {

    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;

    public DocumentoServiceImpl(ExpedienteRepository expedienteRepository, DocumentoRepository documentoRepository) {
        this.expedienteRepository = expedienteRepository;
        this.documentoRepository = documentoRepository;
    }


    @Override
    public void guardar(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento) {

        try {
            Expediente expediente = expedienteRepository.findById(expedienteId)
                    .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

            String nombreOriginal = archivo.getOriginalFilename();
            if (nombreOriginal == null || nombreOriginal.isBlank()) {
                throw new RuntimeException("El archivo no tiene nombre válido");
            }

            String nombreUnico = UUID.randomUUID() + "_" + nombreOriginal;

            Path carpetaUploads = Paths.get("uploads");
            Files.createDirectories(carpetaUploads);

            Path rutaArchivo = carpetaUploads.resolve(nombreUnico);

            Files.copy(archivo.getInputStream(), rutaArchivo, StandardCopyOption.REPLACE_EXISTING);

            Documento doc = new Documento();
            doc.setNombreArchivoOriginal(nombreOriginal);
            doc.setNombreArchivo(nombreUnico);
            doc.setExpediente(expediente);
            doc.setTipoDocumento(tipoDocumento);

            documentoRepository.save(doc);

            System.out.println("Archivo guardado en disco: " + rutaArchivo.toAbsolutePath());
            System.out.println("Documento guardado en BD");

        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    @Override
    public Optional<Documento> buscarPorId(Long id) {
        return documentoRepository.findById(id);
    }


}
