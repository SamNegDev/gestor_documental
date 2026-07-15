package com.example.gestor_documental.dto.expediente;

import com.example.gestor_documental.dto.ia.LecturaIaClienteResponse;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpedienteClienteResponse {
    private Long id;
    private String referencia;
    private String matricula;
    private String tipoTramite;
    private String tipoTramiteDescripcion;
    private String estado;
    private String faseActual;
    private String fechaInicio;
    private Long solicitudId;
    private String siguienteMensaje;
    private ClienteResumenResponse cliente;
    private int mensajesNoLeidos;
    private LecturaIaClienteResponse lecturaIa;

    @Builder.Default
    private List<DocumentoExpedienteResponse> documentos = new ArrayList<>();

    @Builder.Default
    private List<RequisitoDocumentalResponse> requisitosDocumentales = new ArrayList<>();

    @Builder.Default
    private List<IncidenciaExpedienteResponse> incidencias = new ArrayList<>();

    @Builder.Default
    private List<MensajeExpedienteResponse> mensajes = new ArrayList<>();

    @Builder.Default
    private List<HistorialExpedienteResponse> historial = new ArrayList<>();
}
