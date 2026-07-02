package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OpenAiProperties;
import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.util.TextNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentoVehiculoLecturaServiceImpl implements DocumentoVehiculoLecturaService {

    private static final int VEHICULO_RENDER_DPI = 250;
    private static final int VEHICULO_MAX_PAGINAS_PROCESADAS = 3;
    private static final double CONFIANZA_MINIMA_AUTOMATICA = 0.75;

    private final DocumentoService documentoService;
    private final DocumentoVehiculoLecturaRepository lecturaRepository;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(readOnly = true)
    public DocumentoVehiculoLecturaResponse obtenerLectura(Long documentoId, Usuario usuario) {
        documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        return lecturaRepository.findByDocumentoId(documentoId)
                .map(DocumentoVehiculoLecturaResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentoVehiculoLecturaResponse leerVehiculo(Long documentoId, boolean forzar, Usuario usuario) {
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        validarTipoDocumento(documento);
        if (!forzar) {
            DocumentoVehiculoLectura lecturaExistente = lecturaRepository.findByDocumentoId(documentoId).orElse(null);
            if (lecturaExistente != null) {
                return DocumentoVehiculoLecturaResponse.from(lecturaExistente);
            }
        }
        if (!openAiProperties.hasApiKey()) {
            throw new OperacionInvalidaException("OPENAI_API_KEY no esta configurada.");
        }

        Path ruta = resolverRutaDocumento(documento);
        String modeloUsado = modeloLectura();
        JsonNode resultado = llamarOpenAi(documento, ruta, modeloUsado);
        String modeloAvanzado = modeloAvanzado();
        if (debeReintentarVehiculo(resultado) && modeloDistinto(modeloUsado, modeloAvanzado)) {
            modeloUsado = modeloAvanzado;
            resultado = llamarOpenAi(documento, ruta, modeloUsado);
        }

        DocumentoVehiculoLectura lectura = lecturaRepository.findByDocumentoId(documentoId).orElseGet(DocumentoVehiculoLectura::new);
        lectura.setDocumento(documento);
        aplicarResultado(documento, lectura, resultado, modeloUsado);
        lectura = lecturaRepository.save(lectura);
        return DocumentoVehiculoLecturaResponse.from(lectura);
    }

    private void validarTipoDocumento(Documento documento) {
        TipoDocumento tipo = documento.getTipoDocumento();
        if (tipo != TipoDocumento.PERMISO_CIRCULACION
                && tipo != TipoDocumento.FICHA_TECNICA
                && tipo != TipoDocumento.INFORME_DGT) {
            throw new OperacionInvalidaException("Solo se pueden leer datos de vehiculo en permiso, ficha tecnica o Informe DGT.");
        }
    }

    private Path resolverRutaDocumento(Documento documento) {
        Path carpetaUploads = Path.of(uploadDir).toAbsolutePath().normalize();
        Path ruta = carpetaUploads.resolve(documento.getNombreArchivo()).normalize();
        if (!ruta.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida.");
        }
        if (!Files.exists(ruta)) {
            throw new RecursoNoEncontradoException("El archivo fisico del documento no existe.");
        }
        return ruta;
    }

    private JsonNode llamarOpenAi(Documento documento, Path ruta, String modelo) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", modelo);
            payload.set("input", construirInput(documento, ruta));
            payload.set("text", construirFormatoTexto("lectura_vehiculo_documento", esquemaLecturaVehiculo()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiProperties.getResponsesUrl()))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OperacionInvalidaException("OpenAI devolvio HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return objectMapper.readTree(extraerTexto(root));
        } catch (IOException exception) {
            throw new RuntimeException("Error preparando la lectura de vehiculo", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lectura de vehiculo interrumpida", exception);
        }
    }

    private ArrayNode construirInput(Documento documento, Path ruta) throws IOException {
        ArrayNode input = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode texto = objectMapper.createObjectNode();
        texto.put("type", "input_text");
        texto.put("text", promptLecturaVehiculo(documento));
        content.add(texto);

        List<ImagenProcesada> imagenes = procesarDocumento(ruta, documento);
        for (ImagenProcesada imagen : imagenes) {
            ObjectNode nota = objectMapper.createObjectNode();
            nota.put("type", "input_text");
            nota.put("text", "Documento " + documento.getId() + ", imagen " + imagen.nombre()
                    + ". Lee los datos de vehiculo aunque la pagina este girada o escaneada en horizontal.");
            content.add(nota);

            ObjectNode image = objectMapper.createObjectNode();
            image.put("type", "input_image");
            image.put("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(imagen.bytes()));
            content.add(image);
        }

        user.set("content", content);
        input.add(user);
        return input;
    }

    private String promptLecturaVehiculo(Documento documento) {
        return """
                Extrae datos estructurados del vehiculo usando solo este permiso de circulacion, ficha tecnica o Informe DGT.
                Tipo documental esperado: %s.
                El documento puede estar escaneado, fotografiado o girado en horizontal.
                Matricula: normalizala sin espacios ni guiones.
                Bastidor/VIN: suele aparecer como campo E en permiso/ficha o como Bastidor en Informe DGT. No lo confundas con contrasena de homologacion, numero de expediente, CSV, codigo seguro ni numero de permiso.
                Marca: fabricante o marca comercial visible.
                Modelo: modelo, denominacion comercial, version o tipo comercial visible. No uses la marca como modelo si no hay modelo claro.
                Fechas: formato dd/MM/yyyy.
                No inventes datos ni completes desde conocimiento externo.
                Si un dato no aparece con claridad, devuelve null.
                No marques requiereRevision solo porque falte un campo que no esta visible; marcalo solo si algun dato devuelto tiene dudas o el documento no parece de vehiculo.
                Devuelve solo el JSON del esquema.
                """.formatted(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : "");
    }

    private List<ImagenProcesada> procesarDocumento(Path ruta, Documento documento) throws IOException {
        if (esPdf(documento, ruta)) {
            return procesarPdf(ruta);
        }
        BufferedImage imagen = ImageIO.read(ruta.toFile());
        if (imagen == null) {
            throw new OperacionInvalidaException("El documento no se pudo procesar como imagen o PDF.");
        }
        BufferedImage procesada = ampliarSiNecesario(recortarZonaUtil(toRgb(imagen)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(procesada, "png", output);
        return List.of(new ImagenProcesada("vehiculo_imagen_procesada.png", output.toByteArray()));
    }

    private List<ImagenProcesada> procesarPdf(Path ruta) throws IOException {
        List<ImagenProcesada> imagenes = new ArrayList<>();
        try (PDDocument document = PDDocument.load(Files.readAllBytes(ruta))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int paginas = Math.min(document.getNumberOfPages(), VEHICULO_MAX_PAGINAS_PROCESADAS);
            for (int pageIndex = 0; pageIndex < paginas; pageIndex++) {
                BufferedImage render = renderer.renderImageWithDPI(pageIndex, VEHICULO_RENDER_DPI, ImageType.RGB);
                BufferedImage recorte = recortarZonaUtil(render);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(recorte, "png", output);
                imagenes.add(new ImagenProcesada("vehiculo_pagina_" + (pageIndex + 1) + ".png", output.toByteArray()));
            }
        }
        return imagenes;
    }

    private void aplicarResultado(Documento documento, DocumentoVehiculoLectura lectura, JsonNode resultado, String modeloUsado) {
        String matricula = limitar(normalizarMatricula(texto(resultado, "matricula")), 20);
        String marca = limitar(TextNormalizer.upperOrNull(texto(resultado, "marca")), 80);
        String modeloVehiculo = limitar(TextNormalizer.upperOrNull(texto(resultado, "modeloVehiculo")), 120);
        String bastidor = limitar(normalizarBastidor(texto(resultado, "bastidor")), 40);
        Double confianza = numero(resultado, "confianzaGlobal");
        boolean tieneDatos = matricula != null || marca != null || modeloVehiculo != null || bastidor != null;
        boolean requiereRevision = booleano(resultado, "requiereRevision")
                || confianza == null
                || confianza < CONFIANZA_MINIMA_AUTOMATICA
                || !tieneDatos;

        lectura.setTipoDocumentoDetectado(tipoDetectado(texto(resultado, "tipoDocumento"), documento.getTipoDocumento()));
        lectura.setMatricula(matricula);
        lectura.setMarca(marca);
        lectura.setModeloVehiculo(modeloVehiculo);
        lectura.setBastidor(bastidor);
        lectura.setFechaMatriculacion(limitar(texto(resultado, "fechaMatriculacion"), 20));
        lectura.setFechaPrimeraMatriculacion(limitar(texto(resultado, "fechaPrimeraMatriculacion"), 20));
        lectura.setConfianzaGlobal(confianza);
        lectura.setRequiereRevision(requiereRevision);
        lectura.setMensaje(mensajeLectura(matricula, marca, modeloVehiculo, bastidor, requiereRevision));
        lectura.setModelo(modeloUsado);
        lectura.setFechaLectura(LocalDateTime.now());
        lectura.setResultadoJson(resultado.toString());
    }

    private TipoDocumento tipoDetectado(String valor, TipoDocumento fallback) {
        if (valor == null) {
            return fallback;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.contains("INFORME")) {
            return TipoDocumento.INFORME_DGT;
        }
        if (normalizado.contains("FICHA")) {
            return TipoDocumento.FICHA_TECNICA;
        }
        if (normalizado.contains("PERMISO")) {
            return TipoDocumento.PERMISO_CIRCULACION;
        }
        return fallback;
    }

    private String mensajeLectura(String matricula, String marca, String modeloVehiculo, String bastidor, boolean requiereRevision) {
        if (matricula == null && marca == null && modeloVehiculo == null && bastidor == null) {
            return "No se pudieron leer datos de vehiculo con seguridad.";
        }
        if (requiereRevision) {
            return "Vehiculo leido con dudas; revisar antes de consolidar.";
        }
        return "Vehiculo leido con datos suficientes.";
    }

    private ObjectNode construirFormatoTexto(String schemaName, ObjectNode schema) {
        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
        text.set("format", format);
        return text;
    }

    private ObjectNode esquemaLecturaVehiculo() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = objectMapper.createObjectNode();
        props.set("tipoDocumento", nullableString("PERMISO_CIRCULACION, FICHA_TECNICA, INFORME_DGT o null."));
        props.set("matricula", nullableString("Matricula normalizada sin espacios ni separadores."));
        props.set("marca", nullableString("Marca del vehiculo."));
        props.set("modeloVehiculo", nullableString("Modelo, denominacion comercial o version del vehiculo."));
        props.set("bastidor", nullableString("Numero de bastidor/VIN."));
        props.set("fechaMatriculacion", nullableString("Fecha de matriculacion en dd/MM/yyyy."));
        props.set("fechaPrimeraMatriculacion", nullableString("Fecha de primera matriculacion en dd/MM/yyyy."));
        props.set("confianzaGlobal", nullableNumber("Confianza entre 0 y 1."));
        props.set("requiereRevision", nullableBoolean("true si algun dato devuelto no es seguro o el documento no parece de vehiculo."));
        props.set("observaciones", nullableString("Motivo breve si requiere revision."));
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        props.fieldNames().forEachRemaining(required::add);
        schema.set("required", required);
        return schema;
    }

    private ObjectNode nullableString(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("string");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableNumber(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("number");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private ObjectNode nullableBoolean(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode types = objectMapper.createArrayNode();
        types.add("boolean");
        types.add("null");
        node.set("type", types);
        node.put("description", description);
        return node;
    }

    private String extraerTexto(JsonNode root) throws IOException {
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        JsonNode text = part.get("text");
                        if (text != null && text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private String texto(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? null : text;
    }

    private Double numero(JsonNode node, String field) {
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

    private boolean booleano(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return true;
    }

    private String normalizarMatricula(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private String normalizarBastidor(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private String limitar(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private boolean esPdf(Documento documento, Path ruta) {
        String nombre = (documento.getNombreArchivoOriginal() != null ? documento.getNombreArchivoOriginal() : ruta.getFileName().toString())
                .toLowerCase(Locale.ROOT);
        return nombre.endsWith(".pdf") || ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private BufferedImage recortarZonaUtil(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int rowThreshold = Math.max(24, width / 140);
        int colThreshold = Math.max(24, height / 140);

        for (int y = 0; y < height; y++) {
            int count = 0;
            for (int x = 0; x < width; x++) {
                if (esPixelContenido(image.getRGB(x, y))) {
                    count++;
                }
            }
            if (count >= rowThreshold) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        for (int x = 0; x < width; x++) {
            int count = 0;
            for (int y = 0; y < height; y++) {
                if (esPixelContenido(image.getRGB(x, y))) {
                    count++;
                }
            }
            if (count >= colThreshold) {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
        }

        if (maxX < minX || maxY < minY) {
            return ampliarSiNecesario(image);
        }

        int margin = Math.max(45, Math.min(width, height) / 35);
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);

        BufferedImage cropped = image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return ampliarSiNecesario(cropped);
    }

    private boolean esPixelContenido(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int luminance = (red * 30 + green * 59 + blue * 11) / 100;
        return luminance < 232 || max - min > 22;
    }

    private BufferedImage ampliarSiNecesario(BufferedImage image) {
        int minSide = Math.min(image.getWidth(), image.getHeight());
        int maxSide = Math.max(image.getWidth(), image.getHeight());
        if (minSide >= 900 && maxSide <= 1800) {
            return image;
        }
        double scaleUp = 900.0 / Math.max(1, minSide);
        double scaleDown = 1800.0 / Math.max(1, maxSide);
        double scale = minSide < 900 ? Math.min(2.0, scaleUp) : scaleDown;
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private String modeloLectura() {
        if (openAiProperties.getIdentityModel() != null && !openAiProperties.getIdentityModel().isBlank()) {
            return openAiProperties.getIdentityModel();
        }
        return openAiProperties.getModel();
    }

    private String modeloAvanzado() {
        return openAiProperties.getModel() != null && !openAiProperties.getModel().isBlank()
                ? openAiProperties.getModel()
                : modeloLectura();
    }

    private boolean modeloDistinto(String actual, String candidato) {
        return actual != null && candidato != null && !actual.equalsIgnoreCase(candidato);
    }

    private boolean debeReintentarVehiculo(JsonNode resultado) {
        String matricula = normalizarMatricula(texto(resultado, "matricula"));
        String marca = texto(resultado, "marca");
        String modeloVehiculo = texto(resultado, "modeloVehiculo");
        String bastidor = normalizarBastidor(texto(resultado, "bastidor"));
        Double confianza = numero(resultado, "confianzaGlobal");
        return booleano(resultado, "requiereRevision")
                || confianza == null
                || confianza < CONFIANZA_MINIMA_AUTOMATICA
                || (matricula == null && marca == null && modeloVehiculo == null && bastidor == null);
    }

    private record ImagenProcesada(String nombre, byte[] bytes) {
    }
}
