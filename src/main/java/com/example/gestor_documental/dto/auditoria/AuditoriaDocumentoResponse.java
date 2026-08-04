package com.example.gestor_documental.dto.auditoria;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditoriaDocumentoResponse {
    private Long id;
    private String fechaEvento;
    private String accion;
    private String resultado;
    private String recursoTipo;
    private Long recursoId;
    private String recursoNombre;
    private Long documentoId;
    private String documentoNombre;
    private String documentoTipo;
    private Long expedienteId;
    private Long solicitudId;
    private Long clienteId;
    private Long usuarioId;
    private String usuarioEmail;
    private String usuarioRol;
    private String direccionIp;
    private String agenteUsuario;
    private String metodoHttp;
    private String ruta;
    private String detalle;
}
