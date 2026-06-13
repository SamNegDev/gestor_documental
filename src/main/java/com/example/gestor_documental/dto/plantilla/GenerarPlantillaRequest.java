package com.example.gestor_documental.dto.plantilla;

import java.util.Map;

public record GenerarPlantillaRequest(
        String codigo,
        Map<String, String> campos
) {
}
