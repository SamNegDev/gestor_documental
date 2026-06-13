package com.example.gestor_documental.dto.plantilla;

public record PlantillaDestinatarioResponse(
        Long interesadoId,
        String nombre,
        String dni,
        String rol,
        String direccion
) {
}
