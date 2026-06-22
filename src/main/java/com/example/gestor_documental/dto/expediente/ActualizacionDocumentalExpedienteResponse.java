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
public class ActualizacionDocumentalExpedienteResponse {
    private int identidadesLeidas;
    private int operacionesLeidas;
    private int datosAplicados;

    @Builder.Default
    private List<String> avisos = new ArrayList<>();
}
