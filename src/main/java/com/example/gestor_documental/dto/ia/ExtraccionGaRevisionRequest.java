package com.example.gestor_documental.dto.ia;

import com.example.gestor_documental.enums.EstadoRevisionGa;

public record ExtraccionGaRevisionRequest(
        String resultadoIaJson,
        String datosValidadosJson,
        String modelo,
        EstadoRevisionGa estado
) {
}
