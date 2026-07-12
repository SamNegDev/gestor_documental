package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudIdentidadDetectadaRequest {

    private Long documentoId;
    private RolInteresado rol;
    private String tipoDocumentoDetectado;
    private String identificador;
    private String identificadorOriginal;
    private String nombre;
    private String apellido1;
    private String apellido2;
    private String razonSocial;
    private String nombreCompleto;
    private String fechaNacimiento;
    private String fechaCaducidad;
    private String direccionTexto;
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
}
