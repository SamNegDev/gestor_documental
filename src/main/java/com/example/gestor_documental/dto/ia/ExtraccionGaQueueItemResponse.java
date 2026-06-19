package com.example.gestor_documental.dto.ia;

import com.example.gestor_documental.enums.EstadoExtraccionGaJob;
import com.example.gestor_documental.enums.EstadoRevisionGa;

import java.time.LocalDateTime;

public record ExtraccionGaQueueItemResponse(
        Long expedienteId,
        String matricula,
        String clienteNombre,
        String tipoTramite,
        Long revisionId,
        EstadoRevisionGa revisionEstado,
        Double confianzaGlobal,
        boolean requiereRevisionHumana,
        LocalDateTime fechaRevision,
        Long jobId,
        EstadoExtraccionGaJob jobEstado,
        Integer jobProgreso,
        String jobFaseActual,
        String jobMensajeError,
        LocalDateTime jobFechaCreacion
) {
}
