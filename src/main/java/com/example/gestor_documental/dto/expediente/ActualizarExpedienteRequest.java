package com.example.gestor_documental.dto.expediente;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActualizarExpedienteRequest {
    private Long clienteId;
    private Long tipoTramiteId;
    private String matricula;
    private String observaciones;
    private List<InteresadoExpedienteRequest> interesados = new ArrayList<>();
}
