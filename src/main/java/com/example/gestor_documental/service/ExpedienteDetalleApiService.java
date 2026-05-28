package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.model.Usuario;

public interface ExpedienteDetalleApiService {
    ExpedienteDetailResponse obtenerDetalle(Long expedienteId, Usuario usuarioLogueado);
}
