package com.example.gestor_documental.dto.factura;

import java.util.List;

public record FacturaDetalleResponse(
        FacturaHoldedResponse factura,
        List<FacturaVinculacionResponse> vinculaciones
) {}