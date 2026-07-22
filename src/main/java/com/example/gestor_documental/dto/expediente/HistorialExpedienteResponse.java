package com.example.gestor_documental.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialExpedienteResponse {
    private Long id;
    private String accion;
    private String descripcion;
    private String fechaCambio;
    private String usuario;
    private String tipoActividad;
    private String categoria;
    private String audiencia;
}
