package com.example.gestor_documental.dto.expediente;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpedienteVinculadoResponse {
    private Long origenId;
    private String origenReferencia;
    private String origenEstado;
    private String motivoEspera;
    private boolean esperandoFinalizacion;
}
