package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.WhatsappOutboundService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsappOutboundServiceImpl implements WhatsappOutboundService {
    private final RestClient restClient = RestClient.create();

    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.graph-api-version:v23.0}")
    private String graphApiVersion;

    @Value("${app.whatsapp.default-country-code:34}")
    private String defaultCountryCode;

    @Override
    public ResultadoWhatsapp enviarTexto(String destinatario, String mensaje) {
        String telefono = normalizarTelefono(destinatario);
        if (!StringUtils.hasText(telefono)) {
            return ResultadoWhatsapp.error("El cliente no tiene un telefono configurado.");
        }
        if (!envioRealDisponible()) {
            return ResultadoWhatsapp.simulacion();
        }
        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefono,
                    "type", "text",
                    "text", Map.of(
                            "preview_url", false,
                            "body", mensaje != null ? mensaje : ""
                    )
            );
            JsonNode response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.facebook.com")
                            .pathSegment(version(), phoneNumberId.trim(), "messages")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            String messageId = response != null ? response.path("messages").path(0).path("id").asText(null) : null;
            return ResultadoWhatsapp.enviado(messageId);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            return ResultadoWhatsapp.error("WhatsApp ha rechazado el envio (" + ex.getStatusCode().value() + "): "
                    + (StringUtils.hasText(body) ? body : ex.getMessage()));
        } catch (RestClientException | IllegalArgumentException ex) {
            return ResultadoWhatsapp.error("No se pudo enviar por WhatsApp: " + ex.getMessage());
        }
    }

    @Override
    public boolean envioRealDisponible() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId);
    }

    private String version() {
        String value = StringUtils.hasText(graphApiVersion) ? graphApiVersion.trim() : "v23.0";
        return value.startsWith("v") ? value : "v" + value;
    }

    private String normalizarTelefono(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("34") && normalized.length() == 11) {
            return normalized;
        }
        if (normalized.length() == 9 && StringUtils.hasText(defaultCountryCode)) {
            return defaultCountryCode.replaceAll("[^0-9]", "") + normalized;
        }
        return normalized;
    }
}
