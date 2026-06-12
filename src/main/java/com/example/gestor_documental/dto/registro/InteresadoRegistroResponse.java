package com.example.gestor_documental.dto.registro;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InteresadoRegistroResponse {
    private Long id;
    private String dni;
    private String nombre;
    private String telefono;
    private String direccion;
    private String tipoPersona;
    private long totalTramites;
    private String ultimaActividad;
    private List<TramiteRegistroResponse> tramites;
}
