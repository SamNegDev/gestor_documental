package com.example.gestor_documental.dto.expediente;

public record CreacionConProcesamientoResponse(
        Long expedienteId,
        Long solicitudId,
        ProcesamientoExpedienteCompletoResponse procesamiento
) {
}
