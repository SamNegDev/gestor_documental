package com.example.gestor_documental.dto.expediente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FacturaExpedienteResumenResponse(
        Long id,
        String numero,
        LocalDate fechaEmision,
        BigDecimal total,
        BigDecimal importePagado,
        String moneda,
        String estadoFactura,
        String estadoVinculacion,
        int confianza
) {}