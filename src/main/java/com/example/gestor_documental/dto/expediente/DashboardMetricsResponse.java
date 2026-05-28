package com.example.gestor_documental.dto.expediente;

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
public class DashboardMetricsResponse {
    private long totalExpedientes;
    private long enTramite;
    private long finalizados;
    private long incidenciasExpedientes;
    private long totalSolicitudes;
    private long pendienteRevision;
    private long convertidas;
    private long incidenciasSolicitudes;
    private long totalIncidencias;
}
