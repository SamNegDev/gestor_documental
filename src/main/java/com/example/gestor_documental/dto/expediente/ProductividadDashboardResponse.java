package com.example.gestor_documental.dto.expediente;

import java.util.List;

public record ProductividadDashboardResponse(
        String periodo,
        String fechaDesde,
        String fechaHasta,
        long expedientesCreados,
        long expedientesFinalizados,
        double tiempoMedioDias,
        long expedientesEnCurso,
        long incidenciasActivas,
        long expedientesConDocumentacionPendiente,
        List<ProductividadSerieResponse> evolucion,
        List<ProductividadDesgloseResponse> tiemposPorTramite,
        List<ProductividadDesgloseResponse> volumenPorCliente,
        List<ProductividadDesgloseResponse> cuellosBotella
) {
}
