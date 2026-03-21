package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;


public interface DocumentoService {

    void guardar(Long expedienteId, MultipartFile multipartFile, TipoDocumento tipoDocumento);

    Optional<Documento> buscarPorId(Long id);

    Long eliminar(Long id);

    Documento obtenerDocumentoConPermiso(Long documentoId, Usuario usuario);

}
