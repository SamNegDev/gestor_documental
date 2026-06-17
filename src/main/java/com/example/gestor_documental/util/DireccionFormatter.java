package com.example.gestor_documental.util;

import java.util.ArrayList;
import java.util.List;

public final class DireccionFormatter {

    private DireccionFormatter() {
    }

    public static String componer(String tipoVia, String nombreVia, String codigoPostal, String municipio, String provincia) {
        List<String> partes = new ArrayList<>();
        String via = unir(TextNormalizer.upperOrNull(tipoVia), TextNormalizer.upperOrNull(nombreVia));
        if (via != null) {
            partes.add(via);
        }
        String cp = TextNormalizer.upperOrNull(codigoPostal);
        String municipioNormalizado = TextNormalizer.upperOrNull(municipio);
        String provinciaNormalizada = TextNormalizer.upperOrNull(provincia);
        if (cp != null) {
            partes.add(cp);
        }
        if (municipioNormalizado != null) {
            partes.add(municipioNormalizado);
        }
        if (provinciaNormalizada != null) {
            partes.add(provinciaNormalizada);
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    private static String unir(String primero, String segundo) {
        if (primero == null) return segundo;
        if (segundo == null) return primero;
        return primero + " " + segundo;
    }
}
