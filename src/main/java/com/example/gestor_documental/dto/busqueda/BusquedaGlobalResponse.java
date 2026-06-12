package com.example.gestor_documental.dto.busqueda;

import java.util.List;

public record BusquedaGlobalResponse(
        List<BusquedaGlobalItemResponse> expedientes,
        List<BusquedaGlobalItemResponse> solicitudes,
        List<BusquedaGlobalItemResponse> interesados,
        List<BusquedaGlobalItemResponse> vehiculos
) {
    public static BusquedaGlobalResponse vacia() {
        return new BusquedaGlobalResponse(List.of(), List.of(), List.of(), List.of());
    }
}
