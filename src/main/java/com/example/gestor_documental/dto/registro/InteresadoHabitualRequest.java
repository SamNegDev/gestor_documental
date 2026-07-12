package com.example.gestor_documental.dto.registro;

import com.example.gestor_documental.enums.TipoPersona;

public record InteresadoHabitualRequest(
        String dni,
        String nombre,
        String nombrePila,
        String apellido1,
        String apellido2,
        String razonSocial,
        String telefono,
        String direccion,
        String tipoVia,
        String nombreVia,
        String numeroVia,
        String bloque,
        String portal,
        String escalera,
        String piso,
        String puerta,
        String codigoPostal,
        String municipio,
        String provincia,
        TipoPersona tipoPersona
) {
}
