package com.example.gestor_documental.dto.tarea;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TareaResponse {
    private String id;
    private String tipo;
    private String ambito;
    private String prioridad;
    private String titulo;
    private String detalle;
    private String contexto;
    private String entidad;
    private Long entidadId;
    private String matricula;
    private Long clienteId;
    private String cliente;
    private String fechaReferencia;
    private long diasPendiente;
    private String enlace;
    private List<Long> incidenciaIdsAvisoConjunto;
    private String motivoAvisoConjuntoNoDisponible;
}
