package com.example.gestor_documental.dto.expediente;

public record SolicitudPreparacionItemResponse(
        String codigo,
        String etiqueta,
        String estado,
        String detalle,
        String accionTipo,
        String accionLabel
) {
}
