package com.example.gestor_documental.dto.whatsapp;

import com.example.gestor_documental.enums.TipoDocumento;

public record WhatsappAdjuntoClasificarRequest(
        Long expedienteId,
        TipoDocumento tipoDocumento
) {
}
