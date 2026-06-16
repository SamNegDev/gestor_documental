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
public class InteresadoSolicitudResponse {
    private String nombre;
    private String rol;
    private String dni;
    private String telefono;
    private String direccion;
}
