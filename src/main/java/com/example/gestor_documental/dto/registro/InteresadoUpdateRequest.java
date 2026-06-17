package com.example.gestor_documental.dto.registro;

import com.example.gestor_documental.enums.TipoPersona;

public record InteresadoUpdateRequest(
        String dni,
        String nombre,
        String telefono,
        String direccion,
        String tipoVia,
        String nombreVia,
        String codigoPostal,
        String municipio,
        String provincia,
        TipoPersona tipoPersona
) {
}
