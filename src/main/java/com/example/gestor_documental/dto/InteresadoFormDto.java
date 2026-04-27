package com.example.gestor_documental.dto;

import com.example.gestor_documental.enums.RolInteresado;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InteresadoFormDto {


    @Size(max = 120, message = "El nombre no debe tener mas de 120 caracteres")
    private String nombre;
    @Size(max = 20, message = "El DNI/NIE no puede superar los 20 caracteres")
    private String dni;
    @Size(max = 20, message = "El telefono no puede superar los 20 caracteres")
    private String telefono;
    @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
    private String direccion;
    private RolInteresado rol;


}
