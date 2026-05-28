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
public class ExpedienteListItemResponse {
    private Long id;
    private String matricula;
    private String tipoTramite;
    private String estado;
    private String fechaCreacion;
    private String fechaUltimaModificacion;
    private ClienteResumenResponse cliente;
    private UsuarioResumenResponse modificadoPor;
}
