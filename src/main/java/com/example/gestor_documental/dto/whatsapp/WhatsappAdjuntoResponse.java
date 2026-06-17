package com.example.gestor_documental.dto.whatsapp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsappAdjuntoResponse {
    private Long id;
    private String telefono;
    private String tipo;
    private String mimeType;
    private String nombreArchivoOriginal;
    private Long tamanioBytes;
    private String estado;
    private String errorDescarga;
    private String fechaRecepcion;
    private Long clienteId;
    private String cliente;
    private Long expedienteId;
    private String matricula;
    private Long eventoId;
}
