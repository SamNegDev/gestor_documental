package com.example.gestor_documental.dto.plantilla;

public record PlantillaCampoResponse(
        String codigo,
        String etiqueta,
        String valor,
        boolean requerido,
        String tipo,
        String ayuda
) {
}
