package com.example.gestor_documental.dto.registro;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VehiculoRegistroResponse {
    private String matricula;
    private long totalTramites;
    private String ultimaActividad;
    private List<String> interesados;
    private List<TramiteRegistroResponse> tramites;
}
