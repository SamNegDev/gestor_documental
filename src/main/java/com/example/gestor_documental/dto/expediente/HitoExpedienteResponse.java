package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
import java.util.List;

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
public class HitoExpedienteResponse {
    private String id;
    private String titulo;
    private String descripcion;
    private String estado;
    private String tipo;
    private String fecha;
    private String usuario;
    private String nota;
    private String accion;
    private String accionLabel;
    @Builder.Default
    private List<HitoAccionResponse> acciones = new ArrayList<>();
    private boolean completado;
    private boolean bloqueado;
}
