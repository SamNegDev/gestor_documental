package com.example.gestor_documental.dto;

import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.service.InteresadoService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InteresadoFormDto {

    private InteresadoService interesadoService;

    @Size(max = 120, message = "El nombre no debe tener mas de 120 caracteres")
    private String nombre;
    @Size(max = 9, message = "El DNI/NIE no puede superar los 9 caracteres")
    @Pattern(
            regexp = "^([0-9]{8}[A-Za-z]|[XYZ][0-9]{7}[A-Za-z])$",
            message = "El DNI/NIE no tiene un formato válido"
    )
    private String dni;
    @Size(max = 20, message = "El telefono no puede superar los 20 caracteres")
    private String telefono;
    @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
    private String direccion;
    private RolInteresado rol;


}
