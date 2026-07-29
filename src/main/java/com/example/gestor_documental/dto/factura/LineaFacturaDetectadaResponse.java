package com.example.gestor_documental.dto.factura;

public record LineaFacturaDetectadaResponse(
        String documento,
        String matricula,
        String bastidor,
        String compradorIdentificador,
        String compradorNombre,
        Long expedienteId,
        int confianza,
        String estado,
        String motivo,
        boolean confirmacionManualPermitida
) {}
