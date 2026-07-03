package com.example.gestor_documental.dto.expediente;

public record SolicitudPreparacionAccionResponse(
        String tipo,
        String titulo,
        String detalle,
        String bloque,
        String campo,
        String label
) {
    public SolicitudPreparacionAccionResponse(
            String tipo,
            String titulo,
            String detalle,
            String bloque
    ) {
        this(tipo, titulo, detalle, bloque, null, null);
    }
}
