package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.SolicitudLecturaIaJobResponse;
import com.example.gestor_documental.model.Usuario;

public interface SolicitudLecturaIaJobService {
    SolicitudLecturaIaJobResponse crear(Long solicitudId, Usuario usuario, boolean forzarRelectura, String origen, Long documentoId);
    SolicitudLecturaIaJobResponse obtenerUltimo(Long solicitudId, Usuario usuario);
    void crearAutomatico(Long solicitudId, Long usuarioId, String origen);
}
