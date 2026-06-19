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
public class SolicitudDetailResponse {
    private Long id;
    private String matricula;
    private String tipoTramite;
    private String estado;
    private String fechaCreacion;
    private String fechaUltimaModificacion;
    private String observaciones;
    private String situacionDocumental;
    private Long expedienteId;
    private ClienteResumenResponse cliente;
    private UsuarioResumenResponse creadoPor;
    private UsuarioResumenResponse modificadoPor;

    @Builder.Default
    private List<InteresadoSolicitudResponse> interesados = new ArrayList<>();

    @Builder.Default
    private List<DocumentoExpedienteResponse> documentos = new ArrayList<>();

    @Builder.Default
    private List<IncidenciaExpedienteResponse> incidencias = new ArrayList<>();

    @Builder.Default
    private List<HistorialExpedienteResponse> historial = new ArrayList<>();

    @Builder.Default
    private List<MensajeExpedienteResponse> mensajes = new ArrayList<>();
}
