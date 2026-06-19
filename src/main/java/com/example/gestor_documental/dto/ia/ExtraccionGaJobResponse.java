package com.example.gestor_documental.dto.ia;

import com.example.gestor_documental.enums.EstadoExtraccionGaJob;
import com.example.gestor_documental.enums.EstadoRevisionGa;

import java.time.LocalDateTime;

public record ExtraccionGaJobResponse(
        Long id,
        Long expedienteId,
        String matricula,
        String clienteNombre,
        String tipoTramite,
        EstadoExtraccionGaJob estado,
        String modelo,
        Integer progreso,
        String faseActual,
        String mensajeError,
        Integer intentos,
        Long revisionId,
        EstadoRevisionGa revisionEstado,
        Double confianzaGlobal,
        Boolean requiereRevisionHumana,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin
) {
}
