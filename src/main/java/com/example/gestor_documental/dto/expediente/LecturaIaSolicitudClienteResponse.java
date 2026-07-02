package com.example.gestor_documental.dto.expediente;

import java.util.List;

public record LecturaIaSolicitudClienteResponse(
        Long solicitudId,
        boolean apiKeyConfigurada,
        boolean documentacionSuficiente,
        boolean puedeSolicitar,
        List<String> bloqueosDocumentales,
        int usosConsumidos,
        int usosMaximos,
        int usosRestantes,
        int documentosIdentidad,
        int documentosVehiculo,
        int documentosRoles,
        String mensaje
) {
}
