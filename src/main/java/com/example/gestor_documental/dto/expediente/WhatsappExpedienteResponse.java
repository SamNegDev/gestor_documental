package com.example.gestor_documental.dto.expediente;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsappExpedienteResponse {
    private Long id;
    private String telefono;
    private String nombrePerfil;
    private String tipo;
    private String texto;
    private String estado;
    private String fechaRecepcion;
    private String fechaRevision;
    private String revisadoPor;
}
