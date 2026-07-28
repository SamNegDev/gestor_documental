package com.example.gestor_documental.dto.factura;

import java.time.LocalDate;
import java.util.List;

public record AnalisisFacturaArchivoResponse(
        String archivo,
        String numeroFactura,
        LocalDate fechaFactura,
        Long facturaId,
        String estado,
        List<LineaFacturaDetectadaResponse> lineas
) {}