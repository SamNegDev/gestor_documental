package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.enums.RolInteresado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InteresadoExpedienteRequest {
    private String nombre;
    private String dni;
    private String telefono;
    private String direccion;
    private String tipoVia;
    private String nombreVia;
    private String codigoPostal;
    private String municipio;
    private String provincia;
    private RolInteresado rol;
}
