package com.example.gestor_documental.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudDocumentacionIaResponse {

    private Long solicitudId;
    private int documentosIdentidad;
    private int documentosRoles;
    private int lecturasIdentidadNuevas;
    private int lecturasIdentidadReutilizadas;
    private int lecturasRolesNuevas;
    private int lecturasRolesReutilizadas;
    private boolean datosAplicados;
    private boolean yaEstabaCorrecta;
    private boolean requiereRevision;
    private String mensaje;
    private List<String> detalles;
}
