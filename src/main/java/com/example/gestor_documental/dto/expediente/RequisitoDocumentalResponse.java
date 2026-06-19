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
public class RequisitoDocumentalResponse {
    private Long id;
    private String tipoDocumento;
    private String descripcion;
    private String estado;
    private String origen;
    private Long interesadoId;
    private String interesadoNombre;
    private String rolInteresado;
    private Long interesadoRepresentadoId;
    private String interesadoRepresentadoNombre;
    private String rolRepresentado;
    private Long operacionId;
    private String operacionLabel;
    private Long documentoId;
    private String documentoNombre;
    private String motivoOmision;
    private String fechaCreacion;
    private String fechaResolucion;
}
