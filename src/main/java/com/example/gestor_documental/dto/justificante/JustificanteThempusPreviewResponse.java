package com.example.gestor_documental.dto.justificante;

import java.util.List;

public record JustificanteThempusPreviewResponse(
        String method,
        String urlRedacted,
        List<String> headers,
        int bodyBytes,
        String xml
) {
}
