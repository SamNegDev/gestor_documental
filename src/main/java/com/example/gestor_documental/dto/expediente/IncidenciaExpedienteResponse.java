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
public class IncidenciaExpedienteResponse {
    private Long id;
    private String tipo;
    private String observaciones;
    private String fechaCreacion;
    private boolean resuelta;
    private String fechaResolucion;
    private String creadoPor;
    private String resueltoPor;
    private boolean pendienteRevisionCliente;
    @Builder.Default
    private List<DocumentoExpedienteResponse> documentosRevision = new ArrayList<>();
}
