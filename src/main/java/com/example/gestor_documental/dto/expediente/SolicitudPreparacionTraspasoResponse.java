package com.example.gestor_documental.dto.expediente;

import java.util.List;

public record SolicitudPreparacionTraspasoResponse(
        Long solicitudId,
        String estado,
        int progreso,
        SolicitudPreparacionAccionResponse siguienteAccion,
        List<SolicitudPreparacionBloqueResponse> bloques,
        List<SolicitudPreparacionDocumentoResponse> documentosGenerables
) {
}
