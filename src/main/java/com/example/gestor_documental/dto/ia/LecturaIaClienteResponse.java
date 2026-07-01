package com.example.gestor_documental.dto.ia;

import java.util.List;

public record LecturaIaClienteResponse(
        Long expedienteId,
        boolean apiKeyConfigurada,
        boolean documentacionSuficiente,
        boolean puedeSolicitar,
        boolean jobCreado,
        List<String> bloqueosDocumentales,
        int usosConsumidos,
        int usosMaximos,
        int usosRestantes,
        String mensaje,
        ExtraccionGaJobResponse ultimoJob
) {
}
