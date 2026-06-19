package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.model.Usuario;

public interface DocumentoIdentidadLecturaService {

    DocumentoIdentidadLecturaResponse obtenerLectura(Long documentoId, Usuario usuario);

    DocumentoIdentidadLecturaResponse leerIdentidad(Long documentoId, boolean forzar, Usuario usuario);
}
