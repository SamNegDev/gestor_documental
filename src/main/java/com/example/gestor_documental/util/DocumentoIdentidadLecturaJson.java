package com.example.gestor_documental.util;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentoIdentidadLecturaJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DocumentoIdentidadLecturaJson() {
    }

    public static List<IdentidadDetectada> extraer(DocumentoIdentidadLectura lectura) {
        if (lectura == null) {
            return List.of();
        }
        List<IdentidadDetectada> identidades = extraer(lectura.getResultadoJson());
        if (!identidades.isEmpty()) {
            return identidades;
        }
        IdentidadDetectada fallback = new IdentidadDetectada(
                lectura.getTipoDocumentoDetectado() != null ? lectura.getTipoDocumentoDetectado().name() : null,
                lectura.getIdentificador(),
                lectura.getNombre(),
                lectura.getApellido1(),
                lectura.getApellido2(),
                lectura.getRazonSocial(),
                lectura.getFechaNacimiento(),
                lectura.getFechaCaducidad(),
                lectura.getDireccionTexto(),
                lectura.getConfianzaGlobal(),
                lectura.isRequiereRevision(),
                lectura.getMensaje()
        );
        return fallback.tieneDatos() ? List.of(fallback) : List.of();
    }

    public static List<IdentidadDetectada> extraer(String resultadoJson) {
        if (resultadoJson == null || resultadoJson.isBlank()) {
            return List.of();
        }
        try {
            return extraer(OBJECT_MAPPER.readTree(resultadoJson));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static List<IdentidadDetectada> extraer(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        JsonNode identidades = root.path("identidades");
        if (identidades.isArray()) {
            List<IdentidadDetectada> result = new ArrayList<>();
            for (JsonNode item : identidades) {
                IdentidadDetectada identidad = limpiarIdentidad(desdeNodo(item));
                if (identidad.tieneDatos()) {
                    result.add(identidad);
                }
            }
            return unificarIdentidades(result);
        }
        IdentidadDetectada identidad = limpiarIdentidad(desdeNodo(root));
        return identidad.tieneDatos() ? List.of(identidad) : List.of();
    }

    private static IdentidadDetectada desdeNodo(JsonNode node) {
        return new IdentidadDetectada(
                texto(node, "tipoDocumento"),
                texto(node, "identificador"),
                texto(node, "nombre"),
                texto(node, "apellido1"),
                texto(node, "apellido2"),
                texto(node, "razonSocial"),
                texto(node, "fechaNacimiento"),
                texto(node, "fechaCaducidad"),
                texto(node, "direccionTexto"),
                numero(node, "confianzaGlobal"),
                booleano(node, "requiereRevision"),
                primerNoVacio(texto(node, "observaciones"), texto(node, "mensaje"))
        );
    }

    private static String texto(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? null : text;
    }

    private static Double numero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().trim().replace(",", "."));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean booleano(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return true;
    }

    private static String primerNoVacio(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static IdentidadDetectada limpiarIdentidad(IdentidadDetectada identidad) {
        if (identidad == null) {
            return null;
        }
        String identificador = normalizarIdentificador(identidad.identificador());
        if (esCodigoMrzEsp(identificador)) {
            identificador = null;
        }
        return new IdentidadDetectada(
                identidad.tipoDocumento(),
                identificador,
                limpiarTexto(identidad.nombre()),
                limpiarTexto(identidad.apellido1()),
                limpiarTexto(identidad.apellido2()),
                limpiarTexto(identidad.razonSocial()),
                limpiarTexto(identidad.fechaNacimiento()),
                limpiarTexto(identidad.fechaCaducidad()),
                limpiarTexto(identidad.direccionTexto()),
                identidad.confianzaGlobal(),
                identidad.requiereRevision(),
                limpiarTexto(identidad.observaciones())
        );
    }

    private static List<IdentidadDetectada> unificarIdentidades(List<IdentidadDetectada> identidades) {
        List<IdentidadDetectada> result = new ArrayList<>();
        for (IdentidadDetectada identidad : identidades) {
            int index = indiceIdentidadEquivalente(result, identidad);
            if (index >= 0) {
                result.set(index, combinar(result.get(index), identidad));
            } else {
                result.add(identidad);
            }
        }
        return result;
    }

    private static int indiceIdentidadEquivalente(List<IdentidadDetectada> existentes, IdentidadDetectada identidad) {
        for (int index = 0; index < existentes.size(); index++) {
            if (sonMismaIdentidad(existentes.get(index), identidad)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean sonMismaIdentidad(IdentidadDetectada first, IdentidadDetectada second) {
        String firstId = normalizarIdentificador(first.identificador());
        String secondId = normalizarIdentificador(second.identificador());
        if (esIdentificadorDocumento(firstId) && firstId.equals(secondId)) {
            return true;
        }
        String firstName = claveNombre(first);
        String secondName = claveNombre(second);
        if (firstName == null || !firstName.equals(secondName)) {
            return false;
        }
        return !esIdentificadorDocumento(firstId) || !esIdentificadorDocumento(secondId);
    }

    private static IdentidadDetectada combinar(IdentidadDetectada first, IdentidadDetectada second) {
        boolean secondPreferida = puntuacion(second) > puntuacion(first);
        IdentidadDetectada preferida = secondPreferida ? second : first;
        IdentidadDetectada respaldo = secondPreferida ? first : second;
        return new IdentidadDetectada(
                primerNoVacio(preferida.tipoDocumento(), respaldo.tipoDocumento()),
                primerIdentificadorValido(preferida.identificador(), respaldo.identificador()),
                primerNoVacio(preferida.nombre(), respaldo.nombre()),
                primerNoVacio(preferida.apellido1(), respaldo.apellido1()),
                primerNoVacio(preferida.apellido2(), respaldo.apellido2()),
                primerNoVacio(preferida.razonSocial(), respaldo.razonSocial()),
                primerNoVacio(preferida.fechaNacimiento(), respaldo.fechaNacimiento()),
                primerNoVacio(preferida.fechaCaducidad(), respaldo.fechaCaducidad()),
                primerNoVacio(preferida.direccionTexto(), respaldo.direccionTexto()),
                confianzaMaxima(first.confianzaGlobal(), second.confianzaGlobal()),
                first.requiereRevision() && second.requiereRevision(),
                primerNoVacio(preferida.observaciones(), respaldo.observaciones())
        );
    }

    private static int puntuacion(IdentidadDetectada identidad) {
        int score = 0;
        if (esIdentificadorDocumento(identidad.identificador())) {
            score += 6;
        }
        if (identidad.nombre() != null || identidad.razonSocial() != null) {
            score += 2;
        }
        if (identidad.apellido1() != null) {
            score++;
        }
        if (identidad.apellido2() != null) {
            score++;
        }
        if (identidad.direccionTexto() != null) {
            score++;
        }
        score += (int) Math.round((identidad.confianzaGlobal() != null ? identidad.confianzaGlobal() : 0.0) * 10);
        return score;
    }

    private static String primerIdentificadorValido(String first, String second) {
        String firstId = normalizarIdentificador(first);
        String secondId = normalizarIdentificador(second);
        if (esIdentificadorDocumento(firstId)) {
            return firstId;
        }
        if (esIdentificadorDocumento(secondId)) {
            return secondId;
        }
        return primerNoVacio(firstId, secondId);
    }

    private static Double confianzaMaxima(Double first, Double second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return Math.max(first, second);
    }

    private static String claveNombre(IdentidadDetectada identidad) {
        String raw = primerNoVacio(
                identidad.razonSocial(),
                String.join(" ",
                        identidad.nombre() != null ? identidad.nombre() : "",
                        identidad.apellido1() != null ? identidad.apellido1() : "",
                        identidad.apellido2() != null ? identidad.apellido2() : "")
        );
        String normalized = limpiarTexto(raw);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") : null;
    }

    private static String normalizarIdentificador(String value) {
        String normalized = value != null ? value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") : null;
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private static String limpiarTexto(String value) {
        String normalized = value != null ? value.replaceAll("\\s+", " ").trim() : null;
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private static boolean esCodigoMrzEsp(String value) {
        return value != null && value.startsWith("IDESP") && value.length() > 12;
    }

    private static boolean esIdentificadorDocumento(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("[0-9]{8}[A-Z]")
                || value.matches("[XYZ][0-9]{7}[A-Z]")
                || value.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    public record IdentidadDetectada(
            String tipoDocumento,
            String identificador,
            String nombre,
            String apellido1,
            String apellido2,
            String razonSocial,
            String fechaNacimiento,
            String fechaCaducidad,
            String direccionTexto,
            Double confianzaGlobal,
            boolean requiereRevision,
            String observaciones
    ) {
        private boolean tieneDatos() {
            return primerNoVacio(identificador, nombre, apellido1, apellido2, razonSocial) != null;
        }
    }
}
