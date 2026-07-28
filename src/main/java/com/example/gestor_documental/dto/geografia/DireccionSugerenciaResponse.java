package com.example.gestor_documental.dto.geografia;

public record DireccionSugerenciaResponse(
        String codigoPostal,
        String municipio,
        String localidad,
        String provincia,
        String direccion
) {
}
