
package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.TipoDocumento;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface OcrPdfService {
    List<DocumentoDetectadoDto> detectarDocumentos(MultipartFile archivo);

    Optional<TipoDocumento> detectarTipoDocumento(MultipartFile archivo);

    String extraerTextoCompleto(MultipartFile archivo);
}
