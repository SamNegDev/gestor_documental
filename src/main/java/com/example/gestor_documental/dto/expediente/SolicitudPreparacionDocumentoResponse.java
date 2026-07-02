package com.example.gestor_documental.dto.expediente;

import java.util.List;

public record SolicitudPreparacionDocumentoResponse(
        String codigo,
        String nombre,
        String estado,
        int camposCompletos,
        int camposTotales,
        List<String> faltantes
) {
}
