package com.example.gestor_documental.dto.expediente;

public record ProductividadDesgloseResponse(
        String codigo,
        String etiqueta,
        long total,
        double valorMedio
) {
}
