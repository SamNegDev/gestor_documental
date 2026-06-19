package com.example.gestor_documental.util;

public final class NombrePersonaNormalizer {

    private NombrePersonaNormalizer() {
    }

    public static String normalizar(String value) {
        String normalizado = TextNormalizer.upperOrNull(value);
        if (normalizado == null) {
            return null;
        }
        normalizado = normalizado.replaceAll("[.,;:]+", " ");
        normalizado = normalizado.replaceAll("\\bS\\s+L\\s+U\\b", "SLU");
        normalizado = normalizado.replaceAll("\\bS\\s+L\\b", "SL");
        normalizado = normalizado.replaceAll("\\bS\\s+A\\b", "SA");
        normalizado = normalizado.replaceAll("\\bS\\s+C\\b", "SC");
        normalizado = normalizado.replaceAll("\\bSOCIEDAD\\s+LIMITADA\\s+UNIPERSONAL\\b", "SLU");
        normalizado = normalizado.replaceAll("\\bSOCIEDAD\\s+LIMITADA\\b", "SL");
        normalizado = normalizado.replaceAll("\\bSOCIEDAD\\s+ANONIMA\\b", "SA");
        normalizado = normalizado.replaceAll("\\s+", " ").trim();
        return normalizado.isBlank() ? null : normalizado;
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
