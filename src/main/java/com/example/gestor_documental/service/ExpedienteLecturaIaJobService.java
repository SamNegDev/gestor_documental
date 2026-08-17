package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.ExpedienteLecturaIaJobResponse;
import com.example.gestor_documental.model.Usuario;

public interface ExpedienteLecturaIaJobService {
    ExpedienteLecturaIaJobResponse crear(Long expedienteId, Usuario usuario, boolean forzarRelectura, String origen);
    ExpedienteLecturaIaJobResponse obtenerUltimo(Long expedienteId, Usuario usuario);
    void crearAutomatico(Long expedienteId, Long usuarioId, String origen);
}
