package com.example.gestor_documental.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudListItemResponse {
    private Long id;
    private String matricula;
    private String tipoTramite;
    private String estado;
    private String fechaCreacion;
    private String fechaUltimaModificacion;
    private ClienteResumenResponse cliente;
    private UsuarioResumenResponse modificadoPor;
    private Long expedienteId;
    private String situacionDocumental;
    private String siguienteActuacion;
    @Builder.Default
    private List<InteresadoSolicitudResponse> interesados = new ArrayList<>();
}
