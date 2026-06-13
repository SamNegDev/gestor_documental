package com.example.gestor_documental.util;

import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.model.Cliente;

public final class ClienteBrandingUrls {

    private ClienteBrandingUrls() {
    }

    public static String logoUrl(Cliente cliente, TipoLogoCliente tipo) {
        if (cliente == null) {
            return null;
        }
        String storedPath = tipo == TipoLogoCliente.PRINCIPAL
                ? cliente.getLogoPrincipalPath()
                : cliente.getLogoCompactoPath();
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        String version = Integer.toUnsignedString(storedPath.hashCode());
        return "/api/clientes/" + cliente.getId() + "/logos/" + tipo.routeValue() + "?v=" + version;
    }
}
