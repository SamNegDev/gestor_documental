package com.example.gestor_documental.dto.seguimiento;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class SeguimientoIncidenciaResponse {
    private Long incidenciaId;
    private Long expedienteId;
    private String matricula;
    private Long clienteId;
    private String cliente;
    private String tipoIncidencia;
    private String observaciones;
    private int avisosEnviados;
    private int maxAvisos;
    private String fechaPrimerAviso;
    private String fechaUltimoAviso;
    private String proximoAviso;
    private boolean pendienteNotificacion;
    private boolean archivada;
    private String fechaArchivo;
    private int anioExpediente;
}
