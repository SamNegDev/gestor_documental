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
public class ListCatalogsResponse {

    @Builder.Default
    private List<String> estados = new ArrayList<>();

    @Builder.Default
    private List<TipoTramiteResumenResponse> tiposTramite = new ArrayList<>();

    @Builder.Default
    private List<ClienteResumenResponse> clientes = new ArrayList<>();
}
