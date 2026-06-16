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
}
