package com.example.gestor_documental.util;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

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
                IdentidadDetectada identidad = desdeNodo(item);
                if (identidad.tieneDatos()) {
                    result.add(identidad);
                }
            }
            return result;
        }
        IdentidadDetectada identidad = desdeNodo(root);
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
