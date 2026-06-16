package com.example.gestor_documental.dto.expediente;

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
public class ExpedienteDetailResponse {
    private Long id;
    private String referencia;
    private String matricula;
    private String tipoTramite;
    private String tipoTramiteDescripcion;
    private String estado;
    private String faseActual;
    private String fechaInicio;
    private String fechaUltimaModificacion;
    private String observaciones;
    private Long solicitudId;
    private HitoExpedienteResponse siguientePaso;

    private ClienteResumenResponse cliente;
    private UsuarioResumenResponse creadoPor;
    private UsuarioResumenResponse modificadoPor;

    @Builder.Default
    private List<InteresadoExpedienteResponse> interesados = new ArrayList<>();

    @Builder.Default
    private List<DocumentoExpedienteResponse> documentos = new ArrayList<>();

    @Builder.Default
    private List<RequisitoDocumentalResponse> requisitosDocumentales = new ArrayList<>();

    @Builder.Default
    private List<OperacionExpedienteResponse> operaciones = new ArrayList<>();

    @Builder.Default
    private List<HitoExpedienteResponse> hitos = new ArrayList<>();

    @Builder.Default
    private List<IncidenciaExpedienteResponse> incidencias = new ArrayList<>();

    @Builder.Default
    private List<HistorialExpedienteResponse> historial = new ArrayList<>();

    @Builder.Default
    private List<MensajeExpedienteResponse> mensajes = new ArrayList<>();

    @Builder.Default
    private List<WhatsappExpedienteResponse> whatsappMensajes = new ArrayList<>();
}
