package com.example.gestor_documental.dto.seguimiento;

public record NotificacionIncidenciaPreviewResponse(
        Long incidenciaId,
        String destinatario,
        String asunto,
        String mensaje,
        int numeroAviso,
        int maxAvisos,
        boolean envioReal,
        String proveedor
) {}
