package com.example.gestor_documental.util;

import java.util.ArrayList;
import java.util.List;

public final class DireccionFormatter {

    private DireccionFormatter() {
    }

    public static String componer(String tipoVia, String nombreVia, String codigoPostal, String municipio, String provincia) {
        return componer(tipoVia, nombreVia, null, null, null, null, null, null, codigoPostal, municipio, provincia);
    }

    public static String componer(
            String tipoVia,
            String nombreVia,
            String numeroVia,
            String bloque,
            String portal,
            String escalera,
            String piso,
            String puerta,
            String codigoPostal,
            String municipio,
            String provincia
    ) {
        List<String> partes = new ArrayList<>();
        String via = unir(
                TextNormalizer.upperOrNull(tipoVia),
                TextNormalizer.upperOrNull(nombreVia),
                TextNormalizer.upperOrNull(numeroVia),
                conEtiqueta("BLOQ", bloque),
                conEtiqueta("PORTAL", portal),
                conEtiqueta("ESC", escalera),
                conEtiqueta("PISO", piso),
                conEtiqueta("PTA", puerta));
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

    private static String conEtiqueta(String etiqueta, String valor) {
        String valorNormalizado = TextNormalizer.upperOrNull(valor);
        return valorNormalizado != null ? etiqueta + " " + valorNormalizado : null;
    }

    private static String unir(String... valores) {
        List<String> partes = new ArrayList<>();
        for (String valor : valores) {
            String normalizado = TextNormalizer.upperOrNull(valor);
            if (normalizado != null) {
                partes.add(normalizado);
            }
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }
}
