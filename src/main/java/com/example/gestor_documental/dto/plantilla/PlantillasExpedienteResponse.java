package com.example.gestor_documental.dto.plantilla;

import java.util.List;

public record PlantillasExpedienteResponse(
        String referencia,
        String matricula,
        String tipoTramite,
        String cliente,
        List<PlantillaDocumentoItemResponse> plantillas,
        List<PlantillaDestinatarioResponse> interesados
) {
}
