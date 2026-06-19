package com.example.gestor_documental.dto.ia;

import com.example.gestor_documental.enums.EstadoRevisionGa;

import java.time.LocalDateTime;

public record ExtraccionGaRevisionResponse(
        Long id,
        Long expedienteId,
        String matricula,
        String clienteNombre,
        String tipoTramite,
        String modelo,
        EstadoRevisionGa estado,
        Double confianzaGlobal,
        boolean requiereRevisionHumana,
        String resultadoIaJson,
        String datosValidadosJson,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaUltimaModificacion,
        LocalDateTime fechaPreparado,
        LocalDateTime fechaExportado,
        String revisadoPor
) {
}
