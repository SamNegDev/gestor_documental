package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.model.Usuario;

public interface DocumentoRolesLecturaService {

    DocumentoRolesLecturaResponse obtenerLectura(Long documentoId, Usuario usuario);

    DocumentoRolesLecturaResponse leerRoles(Long documentoId, boolean forzar, Usuario usuario);
}
