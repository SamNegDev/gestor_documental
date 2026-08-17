package com.example.gestor_documental.validation;

import java.util.Locale;

public final class IdentificadorFiscalValidator {

    private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String LETRAS_CONTROL_CIF = "JABCDEFGHI";

    private IdentificadorFiscalValidator() {
    }

    public static boolean esValido(String valor) {
        String identificador = normalizar(valor);
        return esDniNieValido(identificador) || esCifValido(identificador);
    }

    public static boolean esDniNieValido(String valor) {
        String identificador = normalizar(valor);
        if (identificador == null) {
            return false;
        }
        if (identificador.matches("[0-9]{8}[A-Z]")) {
            int numero = Integer.parseInt(identificador.substring(0, 8));
            return identificador.charAt(8) == LETRAS_DNI.charAt(numero % 23);
        }
        if (identificador.matches("[XYZ][0-9]{7}[A-Z]")) {
            int prefijo = switch (identificador.charAt(0)) {
                case 'X' -> 0;
                case 'Y' -> 1;
                case 'Z' -> 2;
                default -> -1;
            };
            int numero = Integer.parseInt(prefijo + identificador.substring(1, 8));
            return identificador.charAt(8) == LETRAS_DNI.charAt(numero % 23);
        }
        return false;
    }

    public static boolean esCifValido(String valor) {
        String identificador = normalizar(valor);
        if (identificador == null
                || !identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]")) {
            return false;
        }
        int sumaPares = 0;
        int sumaImpares = 0;
        for (int indice = 1; indice <= 7; indice++) {
            int digito = identificador.charAt(indice) - '0';
            if (indice % 2 == 0) {
                sumaPares += digito;
            } else {
                int doble = digito * 2;
                sumaImpares += doble / 10 + doble % 10;
            }
        }
        int control = (10 - ((sumaPares + sumaImpares) % 10)) % 10;
        char recibido = identificador.charAt(8);
        return recibido == Character.forDigit(control, 10)
                || recibido == LETRAS_CONTROL_CIF.charAt(control);
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }
}
