package com.example.gestor_documental.dto.busqueda;

public record BusquedaGlobalItemResponse(
        String id,
        String titulo,
        String detalle,
        String meta,
        String enlace
) {}
