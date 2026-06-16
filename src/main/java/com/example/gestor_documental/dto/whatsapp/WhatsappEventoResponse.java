package com.example.gestor_documental.dto.whatsapp;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsappEventoResponse {
    private Long id;
    private String messageId;
    private String telefono;
    private String nombrePerfil;
    private String tipo;
    private String texto;
    private String accionCodigo;
    private boolean procesado;
    private String estado;
    private String errorProcesado;
    private String fechaRecepcion;
    private String fechaRevision;
    private String revisadoPor;
    private Long clienteId;
    private String cliente;
    private Long expedienteId;
    private String matricula;
}
