package com.example.gestor_documental.dto.plantilla;

import java.util.List;

public record PlantillaPreviewResponse(
        String codigo,
        String nombre,
        String nombreArchivo,
        String tipoDocumento,
        List<PlantillaCampoResponse> campos,
        List<String> avisos
) {
}
