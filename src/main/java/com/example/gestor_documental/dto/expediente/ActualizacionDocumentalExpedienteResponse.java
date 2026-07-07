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
    private int vehiculosLeidos;
    private int lecturasIdentidadNuevas;
    private int lecturasIdentidadReutilizadas;
    private int lecturasRolesNuevas;
    private int lecturasRolesReutilizadas;
    private int lecturasVehiculoNuevas;
    private int lecturasVehiculoReutilizadas;
    private int datosAplicados;
    private boolean yaEstabaCorrecta;
    private boolean requiereRevision;
    private String mensaje;

    @Builder.Default
    private List<String> avisos = new ArrayList<>();

    @Builder.Default
    private List<String> detalles = new ArrayList<>();
}
