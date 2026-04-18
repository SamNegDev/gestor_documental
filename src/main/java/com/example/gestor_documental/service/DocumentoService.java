package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface DocumentoService {

    void guardar(Long expedienteId, MultipartFile multipartFile, TipoDocumento tipoDocumento);

    Optional<Documento> buscarPorId(Long id);

    Long eliminar(Long id);

    Documento obtenerDocumentoConPermiso(Long documentoId, Usuario usuario);

    void guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    void guardarParaSolicitud(Long solicitudId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    List<Documento> listarPorExpediente(Long id);

    void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Usuario usuario);

    void extraerPaginasDocumento(Long idOriginal, String rangoPaginas, TipoDocumento nuevoTipo, String nuevoNombre, Usuario usuario);
}
