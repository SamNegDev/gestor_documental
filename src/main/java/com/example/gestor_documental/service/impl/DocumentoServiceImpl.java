package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.service.DocumentoService;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class DocumentoServiceImpl implements DocumentoService {

    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;

    public DocumentoServiceImpl(ExpedienteRepository expedienteRepository, DocumentoRepository documentoRepository) {
        this.expedienteRepository = expedienteRepository;
        this.documentoRepository = documentoRepository;
    }


    @Override
    public void guardar(Long expedienteId, MultipartFile archivo) {

        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow();

        Documento doc = new Documento();

        doc.setNombreArchivoOriginal(archivo.getOriginalFilename());
        doc.setExpediente(expediente);

        System.out.println("Archivo: " + archivo.getOriginalFilename());
        System.out.println("Expediente id: " + expedienteId);

        documentoRepository.save(doc);


    }
}
