package com.example.gestor_documental.util;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;

import java.util.Locale;
import java.util.stream.Stream;

public record DireccionEstructurada(
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

    public static DireccionEstructurada of(
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
        DireccionEstructurada direccion = new DireccionEstructurada(
                normalizar(tipoVia),
                normalizar(nombreVia),
                normalizar(numeroVia),
                normalizar(bloque),
                normalizar(portal),
                normalizar(escalera),
                normalizar(piso),
                normalizar(puerta),
                normalizarCodigoPostal(codigoPostal),
                normalizar(municipio),
                normalizar(provincia)
        );
        return direccion.tieneDatos() ? direccion : null;
    }

    public static DireccionEstructurada fromLectura(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return null;
        }
        return of(
                lectura.getTipoVia(),
                lectura.getNombreVia(),
                lectura.getNumeroVia(),
                lectura.getBloque(),
                lectura.getPortal(),
                lectura.getEscalera(),
                lectura.getPiso(),
                lectura.getPuerta(),
                lectura.getCodigoPostal(),
                lectura.getMunicipio(),
                lectura.getProvincia()
        );
    }

    public static DireccionEstructurada combinar(DireccionEstructurada preferida, DireccionEstructurada respaldo) {
        if (preferida == null) {
            return respaldo;
        }
        if (respaldo == null) {
            return preferida;
        }
        return of(
                primerNoVacio(preferida.tipoVia(), respaldo.tipoVia()),
                primerNoVacio(preferida.nombreVia(), respaldo.nombreVia()),
                primerNoVacio(preferida.numeroVia(), respaldo.numeroVia()),
                primerNoVacio(preferida.bloque(), respaldo.bloque()),
                primerNoVacio(preferida.portal(), respaldo.portal()),
                primerNoVacio(preferida.escalera(), respaldo.escalera()),
                primerNoVacio(preferida.piso(), respaldo.piso()),
                primerNoVacio(preferida.puerta(), respaldo.puerta()),
                primerNoVacio(preferida.codigoPostal(), respaldo.codigoPostal()),
                primerNoVacio(preferida.municipio(), respaldo.municipio()),
                primerNoVacio(preferida.provincia(), respaldo.provincia())
        );
    }

    public String textoCompuesto() {
        return DireccionFormatter.componer(tipoVia, nombreVia, numeroVia, bloque, portal, escalera, piso, puerta,
                codigoPostal, municipio, provincia);
    }

    public boolean tieneDatos() {
        return Stream.of(tipoVia, nombreVia, numeroVia, bloque, portal, escalera, piso, puerta, codigoPostal, municipio, provincia)
                .anyMatch(value -> value != null && !value.isBlank());
    }

    public int puntuacion() {
        int score = 0;
        if (nombreVia != null) score += 6;
        if (tipoVia != null) score += 2;
        if (numeroVia != null) score += 2;
        if (codigoPostal != null) score += 4;
        if (municipio != null) score += 3;
        if (provincia != null) score += 2;
        if (bloque != null) score++;
        if (portal != null) score++;
        if (escalera != null) score++;
        if (piso != null) score++;
        if (puerta != null) score++;
        return score;
    }

    private static String normalizar(String value) {
        return TextNormalizer.upperOrNull(value);
    }

    private static String normalizarCodigoPostal(String value) {
        String normalizado = TextNormalizer.upperOrNull(value);
        if (normalizado == null) {
            return null;
        }
        String soloDigitos = normalizado.replaceAll("[^0-9]", "");
        return soloDigitos.length() == 5 ? soloDigitos : normalizado.toUpperCase(Locale.ROOT);
    }

    private static String primerNoVacio(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
