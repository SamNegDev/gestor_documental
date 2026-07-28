package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.geografia.DireccionSugerenciaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class CartoCiudadDireccionService {

    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("(?<!\\d)\\d{5}(?!\\d)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cartociudad.enabled:true}")
    private boolean enabled;

    @Value("${app.cartociudad.candidates-url:https://www.cartociudad.es/geocoder/api/geocoder/candidates}")
    private String candidatesUrl;

    @Value("${app.cartociudad.timeout-seconds:8}")
    private int timeoutSeconds;


    public List<DireccionSugerenciaResponse> sugerencias(String query, int limite) {
        if (!enabled || !StringUtils.hasText(query) || query.trim().length() < 3) return List.of();
        int size = Math.max(1, Math.min(limite, 100));
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String separator = candidatesUrl.contains("?") ? "&" : "?";
            URI uri = URI.create(candidatesUrl + separator + "q=" + encodedQuery + "&limit=" + size);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "gestor-documental/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) return List.of();

            Map<String, DireccionSugerenciaResponse> unique = new LinkedHashMap<>();
            for (JsonNode item : root) {
                for (String codigoPostal : postalCodes(item.path("postalCode").asText(""))) {
                    DireccionSugerenciaResponse candidate = new DireccionSugerenciaResponse(
                            codigoPostal,
                            text(item, "muni"),
                            text(item, "poblacion"),
                            text(item, "province"),
                            text(item, "address")
                    );
                    String key = String.join("|", candidate.codigoPostal(), candidate.municipio(),
                            candidate.localidad(), candidate.provincia());
                    unique.putIfAbsent(key, candidate);
                    if (unique.size() >= size) break;
                }
                if (unique.size() >= size) break;
            }
            return new ArrayList<>(unique.values());
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("No se pudieron consultar sugerencias de CartoCiudad para {}: {}", query, exception.getMessage());
            return List.of();
        }
    }

    public List<DireccionSugerenciaResponse> codigosPostales(String provincia, String municipio) {
        if (!StringUtils.hasText(municipio)) return List.of();
        String query = String.join(" ", municipio.trim(), provincia == null ? "" : provincia.trim()).trim();
        String municipioNormalizado = CatalogoGeograficoService.normalizar(municipio);
        String provinciaNormalizada = CatalogoGeograficoService.normalizar(provincia);
        return sugerencias(query, 100).stream()
                .filter(item -> CatalogoGeograficoService.normalizar(item.municipio()).equals(municipioNormalizado))
                .filter(item -> provinciaNormalizada.isBlank()
                        || CatalogoGeograficoService.normalizar(item.provincia()).equals(provinciaNormalizada))
                .sorted(java.util.Comparator.comparing(DireccionSugerenciaResponse::codigoPostal)
                        .thenComparing(DireccionSugerenciaResponse::localidad,
                                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private static List<String> postalCodes(String value) {
        if (value == null) return List.of();
        List<String> codes = new ArrayList<>();
        Matcher matcher = POSTAL_CODE_PATTERN.matcher(value);
        while (matcher.find()) codes.add(matcher.group());
        return codes;
    }
}
