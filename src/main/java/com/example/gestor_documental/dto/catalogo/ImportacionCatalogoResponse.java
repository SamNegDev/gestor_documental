package com.example.gestor_documental.dto.catalogo;

public record ImportacionCatalogoResponse(
        String tipo,
        int registrosLeidos,
        int registrosImportados,
        int registrosOmitidos,
        boolean reemplazoCompleto,
        String mensaje
) {
}
