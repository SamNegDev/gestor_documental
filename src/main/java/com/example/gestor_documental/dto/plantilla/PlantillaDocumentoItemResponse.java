package com.example.gestor_documental.dto.plantilla;

public record PlantillaDocumentoItemResponse(
        String codigo,
        String nombre,
        String descripcion,
        String tipoDocumento
) {
}
