package com.example.gestor_documental.dto.expediente;

import java.time.LocalDateTime;

public record ProcesamientoExpedienteCompletoResponse(
        String jobId,
        Long expedienteId,
        Long solicitudId,
        Long documentoId,
        String archivo,
        String estado,
        int documentosGenerados,
        String mensaje,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
}
