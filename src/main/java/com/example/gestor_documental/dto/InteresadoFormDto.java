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
    @Size(max = 160, message = "El nombre propio no debe tener mas de 160 caracteres")
    private String nombrePila;
    @Size(max = 160, message = "El primer apellido no debe tener mas de 160 caracteres")
    private String apellido1;
    @Size(max = 160, message = "El segundo apellido no debe tener mas de 160 caracteres")
    private String apellido2;
    @Size(max = 220, message = "La razon social no debe tener mas de 220 caracteres")
    private String razonSocial;
    @Size(max = 20, message = "El DNI/NIE no puede superar los 20 caracteres")
    private String dni;
    @Size(max = 20, message = "El telefono no puede superar los 20 caracteres")
    private String telefono;
    @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
    private String direccion;
    @Size(max = 30, message = "El tipo de via no puede superar los 30 caracteres")
    private String tipoVia;
    @Size(max = 120, message = "El nombre de la via no puede superar los 120 caracteres")
    private String nombreVia;
    @Size(max = 20, message = "El numero de via no puede superar los 20 caracteres")
    private String numeroVia;
    @Size(max = 20, message = "El bloque no puede superar los 20 caracteres")
    private String bloque;
    @Size(max = 20, message = "El portal no puede superar los 20 caracteres")
    private String portal;
    @Size(max = 20, message = "La escalera no puede superar los 20 caracteres")
    private String escalera;
    @Size(max = 20, message = "El piso no puede superar los 20 caracteres")
    private String piso;
    @Size(max = 20, message = "La puerta no puede superar los 20 caracteres")
    private String puerta;
    @Size(max = 10, message = "El codigo postal no puede superar los 10 caracteres")
    private String codigoPostal;
    @Size(max = 80, message = "El municipio no puede superar los 80 caracteres")
    private String municipio;
    @Size(max = 80, message = "La provincia no puede superar los 80 caracteres")
    private String provincia;
    private RolInteresado rol;


}
