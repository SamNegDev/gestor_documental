package com.example.gestor_documental.dto.expediente;

import lombok.Builder;

@Builder
public record SolicitudInteresadoHabitualResponse(
        Long id,
        String dni,
        String nombre,
        String telefono,
        String direccion,
        String tipoVia,
        String nombreVia,
        String codigoPostal,
        String municipio,
        String provincia,
        String tipoPersona,
        int documentos,
        boolean documentoIdentidadAportado
) {
}
