package com.example.gestor_documental.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperacionExpedienteResponse {
    private Long id;
    private String tipo;
    private String label;
    private String estado;
    private Integer orden;
    private String descripcion;
    private boolean bloqueada;
    private String motivoBloqueo;

    @Builder.Default
    private List<HitoExpedienteResponse> hitos = new ArrayList<>();
}
