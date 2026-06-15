package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudBulkConvertResponse {
    private int total;
    private int convertidas;
    private int fallidas;
    @Builder.Default
    private List<SolicitudBulkConvertResult> resultados = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolicitudBulkConvertResult {
        private Long solicitudId;
        private Long expedienteId;
        private boolean convertida;
        private String mensaje;
    }
}
