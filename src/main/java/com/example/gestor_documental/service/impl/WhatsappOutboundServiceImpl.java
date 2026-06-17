package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.WhatsappOutboundService;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
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
import java.util.List;

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

    @Value("${app.whatsapp.menu-image-url:}")
    private String menuImageUrl;

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
    public ResultadoWhatsapp enviarImagen(String destinatario, String imageUrl, String caption) {
        String telefono = normalizarTelefono(destinatario);
        if (!StringUtils.hasText(telefono)) {
            return ResultadoWhatsapp.error("El cliente no tiene un telefono configurado.");
        }
        if (!StringUtils.hasText(imageUrl)) {
            return ResultadoWhatsapp.simulacion();
        }
        if (!envioRealDisponible()) {
            return ResultadoWhatsapp.simulacion();
        }
        try {
            Map<String, Object> image = StringUtils.hasText(caption)
                    ? Map.of("link", imageUrl.trim(), "caption", caption)
                    : Map.of("link", imageUrl.trim());
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefono,
                    "type", "image",
                    "image", image
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
                                                    menuRow("gestapp_estado_tramite", "Estado del tramite", "Solicitar revision del estado"),
                                                    menuRow("gestapp_nueva_solicitud", "Nueva solicitud", "Iniciar un tramite nuevo"),
                                                    menuRow("gestapp_enviar_documentacion", "Enviar documentos", "Abrir el portal de subida"),
                                                    menuRow("gestapp_menu_pendiente", "Documentacion pendiente", "Ver que falta aportar"),
                                                    menuRow("gestapp_contactar_general", "Hablar con gestoria", "Solicitar contacto")
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
    public ResultadoWhatsapp enviarMenuContinuacion(String destinatario, Expediente expediente, String mensaje) {
        String telefono = normalizarTelefono(destinatario);
        if (!StringUtils.hasText(telefono)) {
            return ResultadoWhatsapp.error("El cliente no tiene un telefono configurado.");
        }
        if (!envioRealDisponible()) {
            return ResultadoWhatsapp.simulacion();
        }
        try {
            enviarImagen(telefono, menuImageUrl, null);
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefono,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", textoMenuContinuacion(expediente, mensaje)),
                            "action", Map.of(
                                    "button", "Que hacer ahora",
                                    "sections", java.util.List.of(Map.of(
                                            "title", "Siguiente paso",
                                            "rows", java.util.List.of(
                                                    menuRow("gestapp_subir_mas_documentacion", "Subir documentos", "Aportar mas documentacion"),
                                                    menuRow("gestapp_ver_pendientes", "Que falta aportar", "Consultar documentacion pendiente"),
                                                    menuRow("gestapp_estado_actual", "Consultar estado", "Ver la fase actual"),
                                                    menuRow("gestapp_menu_principal", "Menu principal", "Volver al inicio"),
                                                    menuRow("gestapp_salir", "Salir", "Cerrar esta consulta")
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
    public ResultadoWhatsapp enviarMenuContinuacionSolicitud(String destinatario, Solicitud solicitud, String mensaje) {
        String telefono = normalizarTelefono(destinatario);
        if (!StringUtils.hasText(telefono)) {
            return ResultadoWhatsapp.error("El cliente no tiene un telefono configurado.");
        }
        if (!envioRealDisponible()) {
            return ResultadoWhatsapp.simulacion();
        }
        try {
            enviarImagen(telefono, menuImageUrl, null);
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefono,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", textoMenuContinuacionSolicitud(solicitud, mensaje)),
                            "action", Map.of(
                                    "button", "Que hacer ahora",
                                    "sections", java.util.List.of(Map.of(
                                            "title", "Siguiente paso",
                                            "rows", java.util.List.of(
                                                    menuRow("gestapp_solicitud_aportar_documentos", "Aportar documentos", "Enviar documentacion"),
                                                    menuRow("gestapp_solicitud_estado", "Consultar estado", "Ver estado de la solicitud"),
                                                    menuRow("gestapp_contactar_solicitud", "Hablar con gestoria", "Solicitar contacto"),
                                                    menuRow("gestapp_menu_principal", "Menu principal", "Volver al inicio"),
                                                    menuRow("gestapp_salir", "Salir", "Cerrar esta consulta")
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
    public ResultadoWhatsapp enviarMenuTiposSolicitud(String destinatario, List<TipoTramite> tipos) {
        String telefono = normalizarTelefono(destinatario);
        if (!StringUtils.hasText(telefono)) {
            return ResultadoWhatsapp.error("El cliente no tiene un telefono configurado.");
        }
        if (!envioRealDisponible()) {
            return ResultadoWhatsapp.simulacion();
        }
        List<Map<String, Object>> rows = tipos.stream()
                .filter(tipo -> tipo != null && tipo.isActivo())
                .limit(10)
                .map(tipo -> menuRow("gestapp_solicitud_tipo_" + tipo.getId(), tituloTipoTramite(tipo), "Crear solicitud de " + tituloTipoTramite(tipo)))
                .toList();
        if (rows.isEmpty()) {
            return ResultadoWhatsapp.error("No hay tipos de tramite disponibles.");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefono,
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "body", Map.of("text", "Vamos a crear una solicitud nueva. Elige el tipo de tramite:"),
                            "action", Map.of(
                                    "button", "Elegir tramite",
                                    "sections", java.util.List.of(Map.of(
                                            "title", "Tipos de tramite",
                                            "rows", rows
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

    private String textoMenuContinuacion(Expediente expediente, String mensaje) {
        StringBuilder builder = new StringBuilder();
        if (expediente != null) {
            String matricula = StringUtils.hasText(expediente.getMatricula()) ? expediente.getMatricula().trim().toUpperCase() : "Sin matricula";
            String tramite = expediente.getTipoTramite() != null && StringUtils.hasText(expediente.getTipoTramite().getDescripcion())
                    ? expediente.getTipoTramite().getDescripcion()
                    : "Tramite";
            builder.append("Expediente activo: ").append(matricula).append("\n");
            builder.append("Tramite: ").append(tramite).append("\n\n");
        }
        if (StringUtils.hasText(mensaje)) {
            builder.append(mensaje.trim()).append("\n\n");
        }
        builder.append("Que quieres hacer ahora?");
        return builder.toString();
    }

    private String textoMenuContinuacionSolicitud(Solicitud solicitud, String mensaje) {
        StringBuilder builder = new StringBuilder();
        if (solicitud != null) {
            builder.append("Solicitud activa: SOL-").append(solicitud.getId()).append("\n");
            if (StringUtils.hasText(solicitud.getMatricula())) {
                builder.append("Matricula: ").append(solicitud.getMatricula().trim().toUpperCase()).append("\n");
            }
            if (solicitud.getTipoTramite() != null) {
                builder.append("Tramite: ").append(tituloTipoTramite(solicitud.getTipoTramite())).append("\n");
            }
            builder.append("\n");
        }
        if (StringUtils.hasText(mensaje)) {
            builder.append(mensaje.trim()).append("\n\n");
        }
        builder.append("Que quieres hacer ahora?");
        return builder.toString();
    }

    private String tituloTipoTramite(TipoTramite tipo) {
        String value = StringUtils.hasText(tipo.getDescripcion())
                ? tipo.getDescripcion()
                : tipo.getNombre() != null ? tipo.getNombre().name().replace('_', ' ') : "Tramite";
        return value.length() > 24 ? value.substring(0, 24) : value;
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
