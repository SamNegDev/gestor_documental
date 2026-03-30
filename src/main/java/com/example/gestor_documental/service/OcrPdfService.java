
package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OcrPdfService {
    List<DocumentoDetectadoDto> detectarDocumentos(MultipartFile archivo);
}
