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
public class DashboardResponse {
    private String scope;
    private DashboardMetricsResponse metrics;
    @Builder.Default
    private List<ExpedienteListItemResponse> ultimosExpedientes = new ArrayList<>();
    @Builder.Default
    private List<SolicitudListItemResponse> ultimasSolicitudes = new ArrayList<>();
}
