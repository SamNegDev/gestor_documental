package com.example.gestor_documental.dto.expediente;

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
public class InconsistenciaDocumentalResponse {
    private String codigo;
    private String severidad;
    private String titulo;
    private String detalle;
    private Long requisitoId;
    private Long documentoSugeridoId;
    private String documentoSugeridoNombre;
    private String accionSugerida;
}
