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
    private RolInteresado interesado2Rol;
    private String interesado2Nombre;
    private String interesado2Dni;
    private String interesado2Telefono;
    private String interesado2Direccion;
}
