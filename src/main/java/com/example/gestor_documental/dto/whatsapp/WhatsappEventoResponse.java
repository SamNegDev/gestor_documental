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
    private boolean procesado;
    private String errorProcesado;
    private String fechaRecepcion;
    private Long clienteId;
    private String cliente;
    private Long expedienteId;
    private String matricula;
}
