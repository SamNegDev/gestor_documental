package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RequisitoDocumentalExpedienteService {
    List<RequisitoDocumentalExpediente> sincronizarYListar(
            Expediente expediente,
            List<ExpedienteInteresado> interesados,
            List<Documento> documentos,
            Usuario usuario
    );

    RequisitoDocumentalExpediente crearManual(
            Long expedienteId,
            TipoDocumento tipoDocumento,
            String descripcion,
            Long interesadoId,
            RolInteresado rolInteresado,
            EstadoRequisitoDocumental estadoInicial,
            Usuario usuario
    );

    RequisitoDocumentalExpediente omitir(Long requisitoId, String motivo, Usuario usuario);

    RequisitoDocumentalExpediente reabrir(Long requisitoId, Usuario usuario);

    RequisitoDocumentalExpediente vincularDocumento(Long requisitoId, Long documentoId, Usuario usuario);

    RequisitoDocumentalExpediente subirDocumento(Long requisitoId, MultipartFile archivo, Usuario usuario);
}
