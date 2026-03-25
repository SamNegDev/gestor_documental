package com.example.gestor_documental.dto;

import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.service.InteresadoService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InteresadoFormDto {

    private InteresadoService interesadoService;

    private String nombre;
    private String dni;
    private String telefono;
    private String direccion;
    private RolInteresado rol;


}
