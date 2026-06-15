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

    Documento guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    Documento guardarParaExpediente(Long expedienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Long operacionId, Usuario usuario);

    Documento guardarGeneradoParaExpediente(Long expedienteId, byte[] contenido, TipoDocumento tipoDocumento,
            String nombreArchivoOriginal, String descripcion, Usuario usuario);

    Documento guardarParaIncidencia(Long incidenciaId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    Documento vincularAIncidencia(Long incidenciaId, Long documentoId, Usuario usuario);

    Documento guardarParaCliente(Long clienteId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    Documento guardarParaInteresadoHabitual(Long clienteId, Long interesadoId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    void guardarParaSolicitud(Long solicitudId, MultipartFile archivo, TipoDocumento tipoDocumento, Usuario usuario);

    List<Documento> listarPorExpediente(Long id);

    List<Documento> listarPorCliente(Long id);

    List<Documento> listarPorInteresadoHabitual(Long clienteId, Long interesadoId);

    List<Documento> listarPorSolicitud(Long id);

    void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Usuario usuario);

    void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, Usuario usuario);

    void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, boolean nombreAutomatico, Usuario usuario);

    void actualizarDocumento(Long id, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, boolean actualizarOperacion, boolean nombreAutomatico, Usuario usuario);

    int contarPaginasDocumento(Long id, Usuario usuario);

    byte[] renderizarPaginaDocumento(Long id, int pagina, Usuario usuario);

    void eliminarPaginasDocumento(Long id, String rangoPaginas, Usuario usuario);

    void unirDocumentos(Long documentoPrincipalId, List<Long> documentoIds, TipoDocumento tipoDocumento, String nombreArchivo, Usuario usuario);

    void unirDocumentos(Long documentoPrincipalId, List<Long> documentoIds, TipoDocumento tipoDocumento, String nombreArchivo, Long operacionId, Usuario usuario);

    void extraerPaginasDocumento(Long idOriginal, String rangoPaginas, TipoDocumento nuevoTipo, String nuevoNombre, Usuario usuario);

    void extraerPaginasDocumento(Long idOriginal, String rangoPaginas, TipoDocumento nuevoTipo, String nuevoNombre, Long operacionId, Usuario usuario);
}
