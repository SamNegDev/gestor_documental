package com.example.gestor_documental.dto.ia;

public record ExtraccionGaSincronizacionResponse(
        int interesadosCreados,
        int interesadosActualizados,
        int relacionesCreadas,
        int vehiculosCreados,
        int vehiculosActualizados,
        String mensaje
) {
}
