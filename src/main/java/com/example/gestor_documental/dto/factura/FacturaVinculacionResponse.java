package com.example.gestor_documental.dto.factura;

public record FacturaVinculacionResponse(
        Long id,
        Long expedienteId,
        String matricula,
        String cliente,
        String estadoExpediente,
        String estadoVinculacion,
        String matriculaDetectada,
        String bastidorDetectado,
        String compradorIdentificadorDetectado,
        int confianza,
        String motivoRevision
) {}