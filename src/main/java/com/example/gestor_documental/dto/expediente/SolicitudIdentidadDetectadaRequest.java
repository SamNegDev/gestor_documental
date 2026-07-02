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
}
