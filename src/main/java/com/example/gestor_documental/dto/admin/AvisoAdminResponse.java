package com.example.gestor_documental.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AvisoAdminResponse {
    private Long id;
    private String tipo;
    private String titulo;
    private String detalle;
    private String origen;
    private String fechaCreacion;
    private Long expedienteId;
    private String matricula;
    private Long clienteId;
    private String cliente;
}
