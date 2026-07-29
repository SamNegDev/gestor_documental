package com.example.gestor_documental.dto.factura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalisisFacturaArchivoResponse(
        String archivo,
        String numeroFactura,
        LocalDate fechaFactura,
        BigDecimal total,
        Long facturaId,
        String estado,
        List<LineaFacturaDetectadaResponse> lineas
) {}