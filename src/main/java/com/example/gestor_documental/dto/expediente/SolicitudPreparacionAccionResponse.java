package com.example.gestor_documental.dto.expediente;

public record SolicitudPreparacionAccionResponse(
        String tipo,
        String titulo,
        String detalle,
        String bloque
) {
}
