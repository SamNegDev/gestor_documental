package com.example.gestor_documental.dto.expediente;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteUpsertRequest {
    private String nif;
    private String nombre;
    private String email;
    private String direccion;
    private String telefono;
    private String preferenciaCanal;
    private boolean avisoIncidenciasActivo = true;
    private String horaAvisoIncidencias = "17:00";
    private boolean avisoFinalizadosActivo = true;
    private String horaAvisoFinalizados = "17:00";
}