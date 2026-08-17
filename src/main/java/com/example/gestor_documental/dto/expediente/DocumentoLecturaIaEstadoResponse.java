package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.model.SolicitudLecturaIaItem;
import com.example.gestor_documental.model.ExpedienteLecturaIaItem;
import lombok.*;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoLecturaIaEstadoResponse {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String tipoLectura;
    private String estado;
    private String mensaje;
    private String modelo;
    private Double confianza;
    private String fechaInicio;
    private String fechaFin;

    public static DocumentoLecturaIaEstadoResponse from(SolicitudLecturaIaItem item) {
        if (item == null) return null;
        return DocumentoLecturaIaEstadoResponse.builder()
                .tipoLectura(item.getTipoLectura() != null ? item.getTipoLectura().name() : null)
                .estado(item.getEstado() != null ? item.getEstado().name() : null)
                .mensaje(item.getMensaje())
                .modelo(item.getModelo())
                .confianza(item.getConfianza())
                .fechaInicio(item.getFechaInicio() != null ? FORMATTER.format(item.getFechaInicio()) : null)
                .fechaFin(item.getFechaFin() != null ? FORMATTER.format(item.getFechaFin()) : null)
                .build();
    }

    public static DocumentoLecturaIaEstadoResponse from(ExpedienteLecturaIaItem item) {
        if (item == null) return null;
        return DocumentoLecturaIaEstadoResponse.builder()
                .tipoLectura(item.getTipoLectura() != null ? item.getTipoLectura().name() : null)
                .estado(item.getEstado() != null ? item.getEstado().name() : null)
                .mensaje(item.getMensaje())
                .modelo(item.getModelo())
                .confianza(item.getConfianza())
                .fechaInicio(item.getFechaInicio() != null ? FORMATTER.format(item.getFechaInicio()) : null)
                .fechaFin(item.getFechaFin() != null ? FORMATTER.format(item.getFechaFin()) : null)
                .build();
    }
}
