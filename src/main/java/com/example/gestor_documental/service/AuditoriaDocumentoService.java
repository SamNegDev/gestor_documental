package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoContext;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoResponse;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

public interface AuditoriaDocumentoService {

    AuditoriaDocumentoContext crearContexto(Documento documento, Usuario usuario, HttpServletRequest request);

    AuditoriaDocumentoContext crearContextoIntento(Long documentoId, Usuario usuario, HttpServletRequest request);

    AuditoriaDocumentoContext crearContextoEvento(
            String recursoTipo,
            Long recursoId,
            String recursoNombre,
            Long expedienteId,
            Long solicitudId,
            Long clienteId,
            Usuario usuario,
            HttpServletRequest request);

    void registrar(AuditoriaDocumentoContext contexto, AccionAuditoriaDocumento accion,
                   ResultadoAuditoriaDocumento resultado, String detalle);

    PagedResponse<AuditoriaDocumentoResponse> listar(
            AccionAuditoriaDocumento accion,
            ResultadoAuditoriaDocumento resultado,
            String recursoTipo,
            Long recursoId,
            Long clienteId,
            Long expedienteId,
            Long documentoId,
            Long usuarioId,
            LocalDateTime desde,
            LocalDateTime hasta,
            int pagina,
            int tamanio);
}
