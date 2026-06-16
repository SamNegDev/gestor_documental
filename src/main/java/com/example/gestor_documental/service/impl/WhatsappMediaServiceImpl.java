package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.WhatsappAdjunto;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.WhatsappAdjuntoRepository;
import com.example.gestor_documental.service.WhatsappMediaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WhatsappMediaServiceImpl implements WhatsappMediaService {
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappAdjuntoRepository adjuntoRepository;

    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.graph-api-version:v23.0}")
    private String graphApiVersion;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public void descargarYGuardar(WhatsappWebhookEvento evento, JsonNode message) {
        String tipo = text(message.path("type"));
        JsonNode media = mediaNode(message, tipo);
        String mediaId = text(media.path("id"));
        if (!StringUtils.hasText(mediaId) || adjuntoRepository.existsByMediaId(mediaId)) {
            return;
        }

        WhatsappAdjunto adjunto = new WhatsappAdjunto();
        adjunto.setEvento(evento);
        adjunto.setCliente(evento.getCliente());
        adjunto.setExpediente(evento.getExpediente());
        adjunto.setTelefono(evento.getTelefono());
        adjunto.setTipo(tipo);
        adjunto.setMediaId(mediaId);
        adjunto.setMimeType(text(media.path("mime_type")));
        adjunto.setNombreArchivoOriginal(nombreOriginal(media, mediaId, tipo));

        if (!StringUtils.hasText(accessToken)) {
            adjunto.setErrorDescarga("No hay token de WhatsApp configurado para descargar adjuntos.");
            adjuntoRepository.save(adjunto);
            return;
        }

        try {
            String metadataResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.facebook.com")
                            .pathSegment(version(), mediaId)
                            .build())
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .body(String.class);
            JsonNode metadata = parseJson(metadataResponse);

            if (metadata != null) {
                adjunto.setMimeType(textOrFallback(metadata.path("mime_type"), adjunto.getMimeType()));
                adjunto.setSha256(text(metadata.path("sha256")));
                if (metadata.path("file_size").canConvertToLong()) {
                    adjunto.setTamanioBytes(metadata.path("file_size").asLong());
                }
            }
            String url = metadata != null ? text(metadata.path("url")) : null;
            if (!StringUtils.hasText(url)) {
                adjunto.setErrorDescarga("Meta no devolvio URL de descarga para el adjunto.");
                adjuntoRepository.save(adjunto);
                return;
            }

            byte[] contenido = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .body(byte[].class);
            if (contenido == null || contenido.length == 0) {
                adjunto.setErrorDescarga("El adjunto descargado esta vacio.");
                adjuntoRepository.save(adjunto);
                return;
            }

            String nombre = guardarArchivo(contenido, adjunto.getNombreArchivoOriginal());
            adjunto.setNombreArchivo(nombre);
            adjunto.setTamanioBytes((long) contenido.length);
            adjuntoRepository.save(adjunto);
        } catch (RestClientException | IllegalArgumentException | IOException exception) {
            adjunto.setErrorDescarga(exception.getMessage());
            adjuntoRepository.save(adjunto);
        }
    }

    private JsonNode mediaNode(JsonNode message, String tipo) {
        if (!StringUtils.hasText(tipo)) {
            return MissingNode.getInstance();
        }
        return message.path(tipo);
    }

    private String nombreOriginal(JsonNode media, String mediaId, String tipo) {
        String filename = text(media.path("filename"));
        if (StringUtils.hasText(filename)) {
            return sanitizar(filename);
        }
        String mimeType = text(media.path("mime_type"));
        return "WHATSAPP_" + mediaId + extensionPara(tipo, mimeType);
    }

    private String guardarArchivo(byte[] contenido, String nombreOriginal) throws IOException {
        LocalDate hoy = LocalDate.now();
        Path carpetaBase = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path carpetaWhatsapp = carpetaBase.resolve(Paths.get("whatsapp", String.valueOf(hoy.getYear()), "%02d".formatted(hoy.getMonthValue()))).normalize();
        if (!carpetaWhatsapp.startsWith(carpetaBase)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida");
        }
        Files.createDirectories(carpetaWhatsapp);
        String nombre = UUID.randomUUID() + "_" + sanitizar(nombreOriginal);
        Path destino = carpetaWhatsapp.resolve(nombre).normalize();
        if (!destino.startsWith(carpetaWhatsapp)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida");
        }
        Files.write(destino, contenido);
        return carpetaBase.relativize(destino).toString().replace('\\', '/');
    }

    private String sanitizar(String nombre) {
        String limpio = Paths.get(nombre != null ? nombre : "adjunto").getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (!StringUtils.hasText(limpio) || ".".equals(limpio) || "..".equals(limpio)) {
            return "adjunto.bin";
        }
        return limpio.length() > 180 ? limpio.substring(0, 180) : limpio;
    }

    private String extensionPara(String tipo, String mimeType) {
        Map<String, String> porMime = Map.of(
                "application/pdf", ".pdf",
                "image/jpeg", ".jpg",
                "image/png", ".png",
                "image/webp", ".webp"
        );
        if (StringUtils.hasText(mimeType) && porMime.containsKey(mimeType)) {
            return porMime.get(mimeType);
        }
        if ("image".equals(tipo)) {
            return ".jpg";
        }
        if ("document".equals(tipo)) {
            return ".pdf";
        }
        return ".bin";
    }

    private String version() {
        String value = StringUtils.hasText(graphApiVersion) ? graphApiVersion.trim() : "v23.0";
        return value.startsWith("v") ? value : "v" + value;
    }

    private String text(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText(null) : null;
    }

    private String textOrFallback(JsonNode node, String fallback) {
        String value = text(node);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private JsonNode parseJson(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new RestClientException("Meta devolvio una respuesta no interpretable.");
        }
    }
}
