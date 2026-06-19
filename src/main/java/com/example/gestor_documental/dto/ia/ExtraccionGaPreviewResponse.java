package com.example.gestor_documental.dto.ia;

import java.math.BigDecimal;
import java.util.List;

public record ExtraccionGaPreviewResponse(
        Long expedienteId,
        String matricula,
        String modelo,
        boolean apiKeyConfigurada,
        boolean extraccionDisponible,
        List<String> bloqueosDocumentales,
        int documentosRelevantes,
        int paginasRelevantes,
        long tamanoTotalBytes,
        BigDecimal costeEstimadoMinUsd,
        BigDecimal costeEstimadoMaxUsd,
        List<ExtraccionGaDocumentoSeleccionadoResponse> documentos
) {
}
