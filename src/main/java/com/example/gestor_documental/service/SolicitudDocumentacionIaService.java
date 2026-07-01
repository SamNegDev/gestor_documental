package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.dto.expediente.LecturaIaSolicitudClienteResponse;
import com.example.gestor_documental.model.Usuario;

public interface SolicitudDocumentacionIaService {

    SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin);

    SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin, boolean forzarRelectura);

    SolicitudDocumentacionIaResponse procesarDocumentacionInterna(Long solicitudId, Usuario usuario);

    LecturaIaSolicitudClienteResponse obtenerLecturaCliente(Long solicitudId, Usuario cliente);

    SolicitudDocumentacionIaResponse procesarDocumentacionCliente(Long solicitudId, Usuario cliente);
}
