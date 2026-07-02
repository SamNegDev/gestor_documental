package com.example.gestor_documental.dto.expediente;

import java.util.List;

public record SolicitudPreparacionBloqueResponse(
        String codigo,
        String titulo,
        String estado,
        int completados,
        int total,
        List<SolicitudPreparacionItemResponse> items
) {
}
