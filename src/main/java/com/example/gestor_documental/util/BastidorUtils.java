package com.example.gestor_documental.util;

import java.util.Locale;

public final class BastidorUtils {

    public static final int MAX_LENGTH = 40;
    private static final int MIN_AUTOMATIC_LENGTH = 8;
    private static final int MAX_AUTOMATIC_LENGTH = 20;

    private BastidorUtils() {
    }

    public static String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    public static boolean esLecturaAutomaticaPlausible(String value) {
        String normalizado = normalizar(value);
        if (normalizado == null
                || normalizado.length() < MIN_AUTOMATIC_LENGTH
                || normalizado.length() > MAX_AUTOMATIC_LENGTH) {
            return false;
        }
        return normalizado.matches("[A-HJ-NPR-Z0-9]+");
    }

    public static boolean excedeLongitudMaxima(String value) {
        String normalizado = normalizar(value);
        return normalizado != null && normalizado.length() > MAX_LENGTH;
    }
}
