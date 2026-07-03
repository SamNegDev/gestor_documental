package com.example.gestor_documental.dto.expediente;

public record SolicitudPreparacionItemResponse(
        String codigo,
        String etiqueta,
        String estado,
        String detalle,
        String accionTipo,
        String accionLabel,
        String accionCampo
) {
    public SolicitudPreparacionItemResponse(
            String codigo,
            String etiqueta,
            String estado,
            String detalle,
            String accionTipo,
            String accionLabel
    ) {
        this(codigo, etiqueta, estado, detalle, accionTipo, accionLabel, null);
    }
}
