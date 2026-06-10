package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccionMasivaExpedienteRequest {
    private List<Long> expedienteIds = new ArrayList<>();
    private String accion;
    private String codigoHito;
}
