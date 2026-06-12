package com.example.gestor_documental.dto.tarea;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TareasResumenResponse {
    private long total;
    private long urgentes;
    private long estancados;
}
