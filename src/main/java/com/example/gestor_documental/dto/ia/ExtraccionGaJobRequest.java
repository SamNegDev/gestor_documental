package com.example.gestor_documental.dto.ia;

import java.util.List;

public record ExtraccionGaJobRequest(
        List<Long> expedienteIds,
        String modelo
) {
}
