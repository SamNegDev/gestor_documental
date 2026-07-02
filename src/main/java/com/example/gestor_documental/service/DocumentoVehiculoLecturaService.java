package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.model.Usuario;

public interface DocumentoVehiculoLecturaService {

    DocumentoVehiculoLecturaResponse obtenerLectura(Long documentoId, Usuario usuario);

    DocumentoVehiculoLecturaResponse leerVehiculo(Long documentoId, boolean forzar, Usuario usuario);
}
