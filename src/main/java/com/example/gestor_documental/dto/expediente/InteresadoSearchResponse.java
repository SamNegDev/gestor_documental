package com.example.gestor_documental.dto.expediente;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InteresadoSearchResponse {
    private Long id;
    private String nombre;
    private String nombrePila;
    private String apellido1;
    private String apellido2;
    private String razonSocial;
    private String dni;
    private String telefono;
    private String direccion;
    private String tipoVia;
    private String nombreVia;
    private String numeroVia;
    private String bloque;
    private String portal;
    private String escalera;
    private String piso;
    private String puerta;
    private String codigoPostal;
    private String municipio;
    private String provincia;
    private String tipoPersona;
}
