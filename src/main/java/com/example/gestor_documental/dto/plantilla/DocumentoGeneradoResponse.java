package com.example.gestor_documental.dto.plantilla;

public record DocumentoGeneradoResponse(
        Long documentoId,
        String nombreArchivo,
        String tipoDocumento
) {
}
