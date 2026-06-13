package com.example.gestor_documental.enums;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum TipoLogoCliente {
    PRINCIPAL("principal"),
    COMPACTO("compacto");

    private final String routeValue;

    TipoLogoCliente(String routeValue) {
        this.routeValue = routeValue;
    }

    public String routeValue() {
        return routeValue;
    }

    public static TipoLogoCliente fromRoute(String value) {
        for (TipoLogoCliente tipo : values()) {
            if (tipo.routeValue.equalsIgnoreCase(value)) {
                return tipo;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de logo no valido");
    }
}
