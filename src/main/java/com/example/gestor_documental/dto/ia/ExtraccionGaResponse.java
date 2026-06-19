package com.example.gestor_documental.dto.ia;

import java.util.Map;

public record ExtraccionGaResponse(
        ExtraccionGaPreviewResponse preview,
        boolean ejecutado,
        String resultadoJson,
        Map<String, Object> uso,
        String aviso
) {
}
