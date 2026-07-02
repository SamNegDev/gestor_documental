package com.example.gestor_documental.dto.expediente;

public record SolicitudVehiculoResponse(
        String matricula,
        String marca,
        String modelo,
        String bastidor
) {
}
