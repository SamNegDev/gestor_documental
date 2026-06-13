package com.example.gestor_documental.dto.registro;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VehiculoRegistroResponse {
    private Long id;
    private String matricula;
    private String bastidor;
    private String marca;
    private String modelo;
    private String fechaPrimeraMatriculacion;
    private String observaciones;
    private long totalTramites;
    private String ultimaActividad;
    private List<String> interesados;
    private List<TramiteRegistroResponse> tramites;
}
