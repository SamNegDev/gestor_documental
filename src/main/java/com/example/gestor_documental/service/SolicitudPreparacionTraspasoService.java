package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.model.Usuario;

public interface SolicitudPreparacionTraspasoService {

    SolicitudPreparacionTraspasoResponse obtenerPreparacion(Long solicitudId, Usuario usuario);
}
