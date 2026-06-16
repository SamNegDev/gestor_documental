package com.example.gestor_documental.dto.expediente;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SolicitudInteresadoCoincidenciaResponse {

    private String rol;
    private String dni;
    private String nombreRegistrado;
    private String nombreDeclarado;
    private String telefonoRegistrado;
    private String telefonoDeclarado;
    private String direccionRegistrada;
    private String direccionDeclarada;
    private List<String> camposDiferentes;
}
