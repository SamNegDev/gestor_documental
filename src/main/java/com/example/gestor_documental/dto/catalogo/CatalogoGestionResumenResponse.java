package com.example.gestor_documental.dto.catalogo;

public record CatalogoGestionResumenResponse(
        long personas,
        long representantes,
        long vehiculos,
        boolean personasDisponibles,
        boolean representantesDisponibles,
        boolean vehiculosDisponibles
) {
}
