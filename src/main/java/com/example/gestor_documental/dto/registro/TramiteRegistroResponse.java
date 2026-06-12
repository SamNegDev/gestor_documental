package com.example.gestor_documental.dto.registro;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TramiteRegistroResponse {
    private Long id;
    private String matricula;
    private String tipoTramite;
    private String estado;
    private String rol;
    private String cliente;
    private String fechaUltimaModificacion;
}
