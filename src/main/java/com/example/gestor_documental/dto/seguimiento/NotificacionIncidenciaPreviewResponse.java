package com.example.gestor_documental.dto.seguimiento;

import java.util.List;

public record NotificacionIncidenciaPreviewResponse(
        Long incidenciaId,
        String destinatario,
        List<String> copias,
        String asunto,
        String mensaje,
        int numeroAviso,
        int maxAvisos,
        boolean envioReal,
        String proveedor
) {}
