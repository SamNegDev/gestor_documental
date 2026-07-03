package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudUpsertRequest {
    private Long tipoTramiteId;
    private String matricula;
    private String vehiculoMarca;
    private String vehiculoModelo;
    private String vehiculoBastidor;
    private String operacionPrecioVenta;
    private String observaciones;
    private RolInteresado interesado1Rol;
    private String interesado1Nombre;
    private String interesado1Dni;
    private String interesado1Telefono;
    private String interesado1Direccion;
    private String interesado1TipoVia;
    private String interesado1NombreVia;
    private String interesado1NumeroVia;
    private String interesado1Bloque;
    private String interesado1Portal;
    private String interesado1Escalera;
    private String interesado1Piso;
    private String interesado1Puerta;
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
    private String interesado2NumeroVia;
    private String interesado2Bloque;
    private String interesado2Portal;
    private String interesado2Escalera;
    private String interesado2Piso;
    private String interesado2Puerta;
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
    private String interesado3NumeroVia;
    private String interesado3Bloque;
    private String interesado3Portal;
    private String interesado3Escalera;
    private String interesado3Piso;
    private String interesado3Puerta;
    private String interesado3CodigoPostal;
    private String interesado3Municipio;
    private String interesado3Provincia;
}
