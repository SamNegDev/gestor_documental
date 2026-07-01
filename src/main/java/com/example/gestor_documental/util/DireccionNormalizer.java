package com.example.gestor_documental.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;

public final class DireccionNormalizer {

    private static final Set<String> TOKENS_IGNORABLES = Set.of(
            "CALLE", "CL", "C", "CARR", "CARRETERA", "CR", "CTRA", "AV", "AVDA", "AVENIDA",
            "LUGAR", "LG", "AUTOPISTA", "AU", "DEL", "DE", "LA", "LAS", "LOS", "EL",
            "SANTA", "STA", "CRUZ", "TENERIFE", "TF", "SC", "S", "ESP", "ESPANA", "ESPAÑA"
    );

    private DireccionNormalizer() {
    }

    public static String normalizar(String value) {
        String normalizado = TextNormalizer.upperOrNull(value);
        if (normalizado == null) {
            return null;
        }
        normalizado = Normalizer.normalize(normalizado, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalizado = normalizado.replaceAll("[^A-Z0-9]+", " ");
        normalizado = normalizado.replaceAll("\\b\\d{5}\\b", " ");
        normalizado = normalizado.replaceAll("\\s+", " ").trim();
        if (normalizado.isBlank()) {
            return null;
        }
        String significant = Arrays.stream(normalizado.split(" "))
                .filter(token -> !token.isBlank())
                .filter(token -> !TOKENS_IGNORABLES.contains(token))
                .distinct()
                .sorted()
                .reduce((first, second) -> first + " " + second)
                .orElse("");
        return significant.isBlank() ? normalizado : significant;
    }

    public static boolean equivalentes(String a, String b) {
        String normalizadoA = normalizar(a);
        String normalizadoB = normalizar(b);
        if (normalizadoA == null || normalizadoB == null) {
            return normalizadoA == null && normalizadoB == null;
        }
        return normalizadoA.equals(normalizadoB);
    }
}
