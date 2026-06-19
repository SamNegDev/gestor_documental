package com.example.gestor_documental.dto.ia;

public record ExtraccionGaRequest(
        String modelo,
        Boolean ejecutar
) {
    public boolean debeEjecutar() {
        return Boolean.TRUE.equals(ejecutar);
    }
}
