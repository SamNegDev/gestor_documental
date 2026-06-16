package com.example.gestor_documental.dto.seguimiento;

public record ConfiguracionSeguimientoRequest(
        int diasAviso1,
        int diasAviso2,
        int diasAviso3,
        int diasAviso4,
        int diasAviso5,
        int maxAvisos,
        int diasExpedienteEstancado
) {}
