package com.example.gestor_documental.dto.ia;

import com.example.gestor_documental.enums.TipoDocumento;

public record ExtraccionGaDocumentoSeleccionadoResponse(
        Long id,
        TipoDocumento tipoDocumento,
        String nombreArchivoOriginal,
        int paginas,
        long tamanoBytes
) {
}
