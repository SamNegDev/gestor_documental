package com.example.gestor_documental.validation;

import org.springframework.stereotype.Component;

    @Component
    public class DniNieValidator {

        private static final String LETRAS = "TRWAGMYFPDXBNJZSQVHLCKE";

        public boolean esValido(String valor) {
            if (valor == null) {
                return false;
            }

            String documento = valor.trim().toUpperCase();

            if (documento.matches("\\d{8}[A-Z]")) {
                return validarDni(documento);
            }

            if (documento.matches("[XYZ]\\d{7}[A-Z]")) {
                return validarNie(documento);
            }

            return false;
        }

        private boolean validarDni(String dni) {
            int numero = Integer.parseInt(dni.substring(0, 8));
            char letraEsperada = LETRAS.charAt(numero % 23);
            char letraRecibida = dni.charAt(8);
            return letraEsperada == letraRecibida;
        }

        private boolean validarNie(String nie) {
            char inicial = nie.charAt(0);

            String prefijo;
            switch (inicial) {
                case 'X':
                    prefijo = "0";
                    break;
                case 'Y':
                    prefijo = "1";
                    break;
                case 'Z':
                    prefijo = "2";
                    break;
                default:
                    return false;
            }

            String numeroTransformado = prefijo + nie.substring(1, 8);
            int numero = Integer.parseInt(numeroTransformado);
            char letraEsperada = LETRAS.charAt(numero % 23);
            char letraRecibida = nie.charAt(8);

            return letraEsperada == letraRecibida;
        }
    }

