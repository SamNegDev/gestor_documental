package com.example.gestor_documental.dto.registro;

import java.time.LocalDate;

public record VehiculoUpdateRequest(
        String bastidor,
        String marca,
        String modelo,
        LocalDate fechaPrimeraMatriculacion,
        String observaciones
) {
}
