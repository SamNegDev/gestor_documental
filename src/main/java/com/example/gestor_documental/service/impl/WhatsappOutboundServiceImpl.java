package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.WhatsappOutboundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            JsonNode response = enviar(payload);
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
    public ResultadoWhatsapp enviarAvisoSeguimiento(String destinatario, String mensaje) {
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
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "button",
                            "body", Map.of("text", mensaje != null ? mensaje : ""),
                            "action", Map.of("buttons", java.util.List.of(
                                    quickReply("gestapp_ya_lo_envie", "Ya lo envie"),
                                    quickReply("gestapp_recordar_manana", "Recordarme manana"),
                                    quickReply("gestapp_contactar", "Contactar")
                            ))
                    )
            );
            JsonNode response = enviar(payload);
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
    public ResultadoWhatsapp enviarMenuPrincipal(String destinatario) {
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
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", "Hola, soy GestApp. Elige que necesitas:"),
                            "action", Map.of(
                                    "button", "Abrir menu",
                                    "sections", java.util.List.of(Map.of(
                                            "title", "GestApp",
                                            "rows", java.util.List.of(
                                                    menuRow("gestapp_menu_expedientes", "Mis expedientes", "Consultar tus expedientes"),
                                                    menuRow("gestapp_enviar_documentacion", "Enviar documentos", "Abrir el portal de subida"),
                                                    menuRow("gestapp_menu_pendiente", "Documentacion pendiente", "Ver que falta aportar"),
                                                    menuRow("gestapp_contactar", "Hablar con gestoria", "Solicitar contacto")
                                            )
                                    ))
                            )
                    )
            );
            JsonNode response = enviar(payload);
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

    private JsonNode enviar(Map<String, Object> payload) {
        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .pathSegment(version(), phoneNumberId.trim(), "messages")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken.trim())
                .body(payload)
                .retrieve()
                .body(String.class);
        return parseJson(response);
    }

    private JsonNode parseJson(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new RestClientException("WhatsApp devolvio una respuesta no interpretable.");
        }
    }

    private Map<String, Object> quickReply(String id, String title) {
        return Map.of(
                "type", "reply",
                "reply", Map.of(
                        "id", id,
                        "title", title
                )
        );
    }

    private Map<String, Object> menuRow(String id, String title, String description) {
        return Map.of(
                "id", id,
                "title", title,
                "description", description
        );
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
