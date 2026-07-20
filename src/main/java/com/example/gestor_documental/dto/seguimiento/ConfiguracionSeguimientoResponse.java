package com.example.gestor_documental.dto.seguimiento;

public record ConfiguracionSeguimientoResponse(
        int diasAviso1,
        int diasAviso2,
        int diasAviso3,
        int diasAviso4,
        int diasAviso5,
        int maxAvisos,
        int diasExpedienteEstancado,
        int diasPrimerAviso,
        boolean automatizacionActiva,
        boolean modoSupervisado,
        String diasEnvio,
        int horaEnvio,
        int tamanioLote,
        String canalAutomatico
) {}
