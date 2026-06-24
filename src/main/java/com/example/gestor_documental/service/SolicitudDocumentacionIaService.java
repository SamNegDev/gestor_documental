package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.model.Usuario;

public interface SolicitudDocumentacionIaService {

    SolicitudDocumentacionIaResponse procesarDocumentacion(Long solicitudId, Usuario admin);

    SolicitudDocumentacionIaResponse procesarDocumentacionInterna(Long solicitudId, Usuario usuario);
}
