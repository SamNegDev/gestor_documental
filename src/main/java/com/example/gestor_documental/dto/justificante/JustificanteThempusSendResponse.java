package com.example.gestor_documental.dto.justificante;

public record JustificanteThempusSendResponse(
        boolean enviado,
        boolean enabled,
        int statusCode,
        String responseBody,
        String urlRedacted
) {
}
