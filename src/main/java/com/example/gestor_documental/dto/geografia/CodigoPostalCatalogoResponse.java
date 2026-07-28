package com.example.gestor_documental.dto.geografia;

public record CodigoPostalCatalogoResponse(
        String codigoPostal,
        String localidad,
        String municipio,
        String municipioCodigo,
        String provincia
) {
}
