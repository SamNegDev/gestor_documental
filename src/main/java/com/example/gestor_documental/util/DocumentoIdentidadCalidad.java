package com.example.gestor_documental.util;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson.IdentidadDetectada;
import com.example.gestor_documental.validation.IdentificadorFiscalValidator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class DocumentoIdentidadCalidad {

    private static final double CONFIANZA_VISUAL_ALTA = 0.90;

    private DocumentoIdentidadCalidad() {
    }

    public static Evaluacion evaluar(DocumentoIdentidadLectura lectura, List<IdentidadDetectada> identidadesValidas) {
        if (lectura == null) {
            return new Evaluacion("REVISAR", "Revisar lectura", List.of(), List.of("No hay una lectura disponible."), false, false);
        }
        List<IdentidadDetectada> validas = identidadesValidas != null ? identidadesValidas : List.of();
        boolean validadaManualmente = esValidadaManualmente(lectura);
        boolean identificadorValido = IdentificadorFiscalValidator.esValido(lectura.getIdentificador());
        boolean vinculoExacto = identificadorValido
                && lectura.getInteresadoVinculado() != null
                && mismoIdentificador(lectura.getIdentificador(), lectura.getInteresadoVinculado().getDni());
        ComparacionInteresado comparacion = comparar(lectura.getInteresadoVinculado(), lectura);

        List<String> indicadores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        if (validadaManualmente) {
            indicadores.add("Revisada manualmente");
        }
        if (identificadorValido) {
            indicadores.add("Identificador fiscal válido");
        }
        if (vinculoExacto) {
            indicadores.add("Vinculada por identificador");
        }
        if (validas.size() == 1) {
            indicadores.add("Una identidad válida");
        }
        if (!validadaManualmente && lectura.getConfianzaGlobal() != null
                && lectura.getConfianzaGlobal() >= CONFIANZA_VISUAL_ALTA) {
            indicadores.add("Lectura visual clara");
        }

        if (!identificadorValido || validas.isEmpty()) {
            advertencias.add("No se ha validado un DNI, NIE o CIF español.");
        }
        if (validas.size() > 1) {
            advertencias.add("El documento contiene varias identidades válidas.");
        }
        if (comparacion.nombreDiferente()) {
            advertencias.add("El nombre leído difiere del interesado guardado.");
        }
        if (comparacion.direccionDiferente()) {
            advertencias.add("La dirección leída es distinta y no se ha reemplazado.");
        }
        if (lectura.isRequiereRevision() && advertencias.isEmpty()) {
            advertencias.add("La lectura contiene datos que conviene comprobar.");
        }

        boolean datosDiferentes = comparacion.tieneDiferencias();
        if (validadaManualmente) {
            return new Evaluacion("CONFIRMADA", "Revisada manualmente", indicadores, advertencias, datosDiferentes, true);
        }
        if (datosDiferentes) {
            return new Evaluacion("CON_DIFERENCIAS", "Datos diferentes", indicadores, advertencias, true, false);
        }
        if (lectura.isRequiereRevision() || !identificadorValido || validas.isEmpty() || validas.size() > 1) {
            return new Evaluacion("REVISAR", "Revisar lectura", indicadores, advertencias, false, false);
        }
        if (vinculoExacto) {
            return new Evaluacion("CONSISTENTE", "Identidad vinculada", indicadores, advertencias, false, false);
        }
        return new Evaluacion("CONSISTENTE", "Lectura consistente", indicadores, advertencias, false, false);
    }

    public static ComparacionInteresado comparar(Interesado interesado, IdentidadDetectada identidad) {
        if (interesado == null || identidad == null) {
            return ComparacionInteresado.SIN_DIFERENCIAS;
        }
        String nombreLectura = primerNoVacio(
                identidad.razonSocial(),
                unir(identidad.nombre(), identidad.apellido1(), identidad.apellido2()));
        return comparar(interesado, nombreLectura, identidad.direccionTexto());
    }

    private static ComparacionInteresado comparar(Interesado interesado, DocumentoIdentidadLectura lectura) {
        if (interesado == null) {
            return ComparacionInteresado.SIN_DIFERENCIAS;
        }
        String nombreLectura = primerNoVacio(
                lectura.getRazonSocial(),
                unir(lectura.getNombre(), lectura.getApellido1(), lectura.getApellido2()));
        return comparar(interesado, nombreLectura, lectura.getDireccionTexto());
    }

    private static ComparacionInteresado comparar(Interesado interesado, String nombreLectura, String direccionLectura) {
        String nombreGuardado = primerNoVacio(interesado.getRazonSocial(), interesado.getNombre());
        boolean nombreDiferente = ambosConValor(nombreGuardado, nombreLectura)
                && !claveNombre(nombreGuardado).equals(claveNombre(nombreLectura));
        boolean direccionDiferente = ambosConValor(interesado.getDireccion(), direccionLectura)
                && !direccionesEquivalentes(interesado.getDireccion(), direccionLectura);
        return new ComparacionInteresado(nombreDiferente, direccionDiferente);
    }

    private static boolean esValidadaManualmente(DocumentoIdentidadLectura lectura) {
        String mensaje = normalizarTexto(lectura.getMensaje());
        return mensaje != null && (mensaje.contains("VALIDADA MANUALMENTE") || mensaje.contains("CORREGIDA MANUALMENTE"));
    }

    private static boolean mismoIdentificador(String first, String second) {
        String firstNormalizado = IdentificadorFiscalValidator.normalizar(first);
        String secondNormalizado = IdentificadorFiscalValidator.normalizar(second);
        return firstNormalizado != null && firstNormalizado.equals(secondNormalizado);
    }

    private static String claveNombre(String value) {
        return tokens(value).stream().sorted().collect(Collectors.joining("|"));
    }

    private static boolean direccionesEquivalentes(String first, String second) {
        String firstNormalizada = normalizarDireccion(first);
        String secondNormalizada = normalizarDireccion(second);
        if (firstNormalizada.equals(secondNormalizada)
                || firstNormalizada.contains(secondNormalizada)
                || secondNormalizada.contains(firstNormalizada)) {
            return true;
        }
        Set<String> firstTokens = tokens(firstNormalizada);
        Set<String> secondTokens = tokens(secondNormalizada);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return false;
        }
        Set<String> interseccion = new LinkedHashSet<>(firstTokens);
        interseccion.retainAll(secondTokens);
        Set<String> union = new LinkedHashSet<>(firstTokens);
        union.addAll(secondTokens);
        return (double) interseccion.size() / union.size() >= 0.72;
    }

    private static String normalizarDireccion(String value) {
        String normalizada = normalizarTexto(value);
        if (normalizada == null) {
            return "";
        }
        return normalizada
                .replaceAll("\\bC\\b|\\bCL\\b", "CALLE")
                .replaceAll("\\bAVDA\\b|\\bAV\\b", "AVENIDA")
                .replaceAll("\\bCTRA\\b", "CARRETERA")
                .replaceAll("\\bNUM\\b|\\bN\\b", "NUMERO");
    }

    private static Set<String> tokens(String value) {
        String normalizada = normalizarTexto(value);
        if (normalizada == null) {
            return Set.of();
        }
        return Arrays.stream(normalizada.split("[^A-Z0-9]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizarTexto(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String unir(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" "));
    }

    private static String primerNoVacio(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private static boolean ambosConValor(String first, String second) {
        return first != null && !first.isBlank() && second != null && !second.isBlank();
    }

    public record Evaluacion(
            String nivel,
            String etiqueta,
            List<String> indicadores,
            List<String> advertencias,
            boolean datosDifierenInteresado,
            boolean validadaManualmente
    ) {
    }

    public record ComparacionInteresado(boolean nombreDiferente, boolean direccionDiferente) {
        private static final ComparacionInteresado SIN_DIFERENCIAS = new ComparacionInteresado(false, false);

        public boolean tieneDiferencias() {
            return nombreDiferente || direccionDiferente;
        }
    }
}
