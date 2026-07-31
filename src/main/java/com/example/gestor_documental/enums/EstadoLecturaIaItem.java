package com.example.gestor_documental.enums;

public enum EstadoLecturaIaItem {
    PENDIENTE,
    PROCESANDO,
    COMPLETADO,
    REQUIERE_REVISION,
    ERROR;

    public boolean activo() {
        return this == PENDIENTE || this == PROCESANDO;
    }
}
