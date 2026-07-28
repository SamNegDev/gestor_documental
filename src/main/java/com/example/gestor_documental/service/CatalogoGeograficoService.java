package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.geografia.CodigoPostalCatalogoResponse;
import com.example.gestor_documental.dto.geografia.MunicipioCatalogoResponse;
import com.example.gestor_documental.dto.geografia.ProvinciaCatalogoResponse;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CatalogoGeograficoService {

    private final Resource municipiosResource;
    private final Resource codigosPostalesGestionTraficoResource;
    private final Resource codigosPostalesResource;
    private List<MunicipioCatalogoResponse> municipios = List.of();
    private List<ProvinciaCatalogoResponse> provincias = List.of();
    private List<CodigoPostalCatalogoResponse> codigosPostales = List.of();

    public CatalogoGeograficoService(
            @Value("classpath:catalogos/municipios_ine_2026.csv") Resource municipiosResource,
            @Value("classpath:catalogos/codigos_postales_gestion_trafico.tsv") Resource codigosPostalesGestionTraficoResource,
            @Value("classpath:catalogos/codigos_postales_geonames_es.tsv") Resource codigosPostalesResource
    ) {
        this.municipiosResource = municipiosResource;
        this.codigosPostalesGestionTraficoResource = codigosPostalesGestionTraficoResource;
        this.codigosPostalesResource = codigosPostalesResource;
    }

    @PostConstruct
    void cargarCatalogo() {
        List<MunicipioCatalogoResponse> cargados = new ArrayList<>(8_200);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                municipiosResource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().skip(1).forEach(line -> {
                List<String> values = parseCsv(line);
                if (values.size() < 6) return;
                String provinciaCodigo = values.get(1);
                String municipioCodigo = provinciaCodigo + values.get(3);
                cargados.add(new MunicipioCatalogoResponse(
                        municipioCodigo,
                        values.get(5),
                        provinciaCodigo,
                        values.get(2)
                ));
            });
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar el catálogo oficial de municipios", exception);
        }

        municipios = cargados.stream()
                .sorted(Comparator.comparing(MunicipioCatalogoResponse::provincia, CatalogoGeograficoService::comparar)
                        .thenComparing(MunicipioCatalogoResponse::nombre, CatalogoGeograficoService::comparar))
                .toList();

        Map<String, ProvinciaCatalogoResponse> unicas = new LinkedHashMap<>();
        municipios.forEach(municipio -> unicas.putIfAbsent(
                municipio.provinciaCodigo(),
                new ProvinciaCatalogoResponse(municipio.provinciaCodigo(), municipio.provincia())
        ));
        provincias = unicas.values().stream()
                .sorted(Comparator.comparing(ProvinciaCatalogoResponse::nombre, CatalogoGeograficoService::comparar))
                .toList();

        Map<String, MunicipioCatalogoResponse> municipiosPorCodigo = municipios.stream()
                .collect(java.util.stream.Collectors.toMap(MunicipioCatalogoResponse::codigo, item -> item));
        List<CodigoPostalCatalogoResponse> postales = new ArrayList<>();
        Set<String> codigosGestionTrafico = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                codigosPostalesGestionTraficoResource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().skip(1).forEach(line -> {
                String[] values = line.split("\\t", -1);
                if (values.length < 3 || !values[2].matches("\\d{5}")) return;
                MunicipioCatalogoResponse municipio = municipiosPorCodigo.get(values[0]);
                if (municipio == null || values[1].isBlank()) return;
                codigosGestionTrafico.add(values[2]);
                postales.add(new CodigoPostalCatalogoResponse(
                        values[2], values[1], municipio.nombre(), municipio.codigo(), municipio.provincia()
                ));
            });
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar el catálogo postal de Gestión Tráfico", exception);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                codigosPostalesResource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> {
                String[] values = line.split("\\t", -1);
                if (values.length < 9 || !values[1].matches("\\d{5}")) return;
                if (codigosGestionTrafico.contains(values[1])) return;
                if (!values[8].isBlank() && !values[8].startsWith(values[1].substring(0, 2))) return;
                MunicipioCatalogoResponse municipio = municipiosPorCodigo.get(values[8]);
                postales.add(new CodigoPostalCatalogoResponse(
                        values[1],
                        values[2],
                        municipio != null ? municipio.nombre() : values[7],
                        values[8],
                        municipio != null ? municipio.provincia() : values[5]
                ));
            });
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar el catálogo de códigos postales y localidades", exception);
        }
        codigosPostales = postales.stream()
                .distinct()
                .sorted(Comparator.comparing(CodigoPostalCatalogoResponse::codigoPostal)
                        .thenComparing(CodigoPostalCatalogoResponse::localidad, CatalogoGeograficoService::comparar))
                .toList();
    }

    public List<ProvinciaCatalogoResponse> provincias() {
        return provincias;
    }

    public PagedResponse<MunicipioCatalogoResponse> buscarMunicipios(
            String provincia,
            String query,
            int pagina,
            int tamanio
    ) {
        String provinciaNormalizada = normalizar(provincia);
        String queryNormalizada = normalizar(query);
        Stream<MunicipioCatalogoResponse> stream = municipios.stream();
        if (!provinciaNormalizada.isBlank()) {
            stream = stream.filter(municipio ->
                    normalizar(municipio.provinciaCodigo()).equals(provinciaNormalizada)
                            || normalizar(municipio.provincia()).equals(provinciaNormalizada));
        }
        if (!queryNormalizada.isBlank()) {
            stream = stream.filter(municipio ->
                    normalizar(municipio.nombre()).contains(queryNormalizada)
                            || normalizar(municipio.codigo()).startsWith(queryNormalizada));
        }
        return PagedResponse.of(stream.toList(), pagina, tamanio);
    }

    public List<CodigoPostalCatalogoResponse> buscarCodigosPostales(String provincia, String municipio) {
        Optional<MunicipioCatalogoResponse> municipioOficial = resolverMunicipio(provincia, municipio);
        String codigoMunicipio = municipioOficial.map(MunicipioCatalogoResponse::codigo).orElse("");
        String municipioNormalizado = normalizar(municipio);
        return codigosPostales.stream()
                .filter(item -> !codigoMunicipio.isBlank()
                        ? codigoMunicipio.equals(item.municipioCodigo())
                        : normalizar(item.municipio()).equals(municipioNormalizado))
                .toList();
    }

    public List<CodigoPostalCatalogoResponse> buscarPorCodigoPostal(String codigoPostal) {
        String codigo = codigoPostal == null ? "" : codigoPostal.replaceAll("\\D", "");
        if (codigo.length() != 5) return List.of();
        return codigosPostales.stream()
                .filter(item -> item.codigoPostal().equals(codigo))
                .toList();
    }
    public Optional<MunicipioCatalogoResponse> resolverMunicipio(String provincia, String municipio) {
        String provinciaNormalizada = normalizar(provincia);
        String municipioNormalizado = normalizar(municipio);
        if (municipioNormalizado.isBlank()) return Optional.empty();
        return municipios.stream()
                .filter(item -> provinciaNormalizada.isBlank()
                        || normalizar(item.provincia()).equals(provinciaNormalizada)
                        || normalizar(item.provinciaCodigo()).equals(provinciaNormalizada))
                .filter(item -> normalizar(item.nombre()).equals(municipioNormalizado))
                .findFirst();
    }

    static String normalizar(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static int comparar(String left, String right) {
        return normalizar(left).compareTo(normalizar(right));
    }

    private static List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values;
    }
}
