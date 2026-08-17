package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.model.ExpedienteLecturaIaItem;
import com.example.gestor_documental.model.ExpedienteLecturaIaJob;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpedienteLecturaIaJobResponse {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Long id;
    private Long expedienteId;
    private String estado;
    private String origen;
    private boolean forzarRelectura;
    private int totalItems;
    private int itemsProcesados;
    private int itemsRevision;
    private int itemsError;
    private int progreso;
    private String faseActual;
    private String mensaje;
    private String fechaCreacion;
    private String fechaInicio;
    private String fechaFin;
    private List<Item> items;

    public static ExpedienteLecturaIaJobResponse from(ExpedienteLecturaIaJob job) {
        if (job == null) return null;
        return ExpedienteLecturaIaJobResponse.builder()
                .id(job.getId())
                .expedienteId(job.getExpediente() != null ? job.getExpediente().getId() : null)
                .estado(job.getEstado() != null ? job.getEstado().name() : null)
                .origen(job.getOrigen())
                .forzarRelectura(job.isForzarRelectura())
                .totalItems(job.getTotalItems())
                .itemsProcesados(job.getItemsProcesados())
                .itemsRevision(job.getItemsRevision())
                .itemsError(job.getItemsError())
                .progreso(job.getProgreso())
                .faseActual(job.getFaseActual())
                .mensaje(job.getMensaje())
                .fechaCreacion(format(job.getFechaCreacion()))
                .fechaInicio(format(job.getFechaInicio()))
                .fechaFin(format(job.getFechaFin()))
                .items(job.getItems() == null ? List.of() : job.getItems().stream().map(Item::from).toList())
                .build();
    }

    private static String format(LocalDateTime value) {
        return value != null ? FORMATTER.format(value) : null;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private Long documentoId;
        private String tipoLectura;
        private String estado;
        private int intentos;
        private String modelo;
        private String versionPrompt;
        private Double confianza;
        private String mensaje;
        private String fechaInicio;
        private String fechaFin;
        private Long duracionMs;

        private static Item from(ExpedienteLecturaIaItem item) {
            return Item.builder()
                    .id(item.getId())
                    .documentoId(item.getDocumento() != null ? item.getDocumento().getId() : null)
                    .tipoLectura(item.getTipoLectura() != null ? item.getTipoLectura().name() : null)
                    .estado(item.getEstado() != null ? item.getEstado().name() : null)
                    .intentos(item.getIntentos())
                    .modelo(item.getModelo())
                    .versionPrompt(item.getVersionPrompt())
                    .confianza(item.getConfianza())
                    .mensaje(item.getMensaje())
                    .fechaInicio(format(item.getFechaInicio()))
                    .fechaFin(format(item.getFechaFin()))
                    .duracionMs(item.getDuracionMs())
                    .build();
        }
    }
}
