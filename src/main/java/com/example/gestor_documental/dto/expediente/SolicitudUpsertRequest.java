package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudUpsertRequest {
    private Long tipoTramiteId;
    private String matricula;
    private String observaciones;
    private RolInteresado interesado1Rol;
    private String interesado1Nombre;
    private String interesado1Dni;
    private String interesado1Telefono;
    private String interesado1Direccion;
    private String interesado1TipoVia;
    private String interesado1NombreVia;
    private String interesado1CodigoPostal;
    private String interesado1Municipio;
    private String interesado1Provincia;
    private RolInteresado interesado2Rol;
    private String interesado2Nombre;
    private String interesado2Dni;
    private String interesado2Telefono;
    private String interesado2Direccion;
    private String interesado2TipoVia;
    private String interesado2NombreVia;
    private String interesado2CodigoPostal;
    private String interesado2Municipio;
    private String interesado2Provincia;
    private RolInteresado interesado3Rol;
    private String interesado3Nombre;
    private String interesado3Dni;
    private String interesado3Telefono;
    private String interesado3Direccion;
    private String interesado3TipoVia;
    private String interesado3NombreVia;
    private String interesado3CodigoPostal;
    private String interesado3Municipio;
    private String interesado3Provincia;
}
