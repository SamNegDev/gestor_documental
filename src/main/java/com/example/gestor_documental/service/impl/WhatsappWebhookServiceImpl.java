package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.service.WhatsappWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsappWebhookServiceImpl implements WhatsappWebhookService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappWebhookEventoRepository eventoRepository;
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;

    @Value("${app.whatsapp.webhook.verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    @Override
    @Transactional
    public void procesar(String payload, String signature) {
        if (!firmaValida(payload, signature)) {
            throw new IllegalArgumentException("Firma de WhatsApp no valida.");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
            JsonNode message = value.path("messages").path(0);
            String messageId = text(message.path("id"));
            if (StringUtils.hasText(messageId) && eventoRepository.existsByMessageId(messageId)) {
                return;
            }

            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload);
            evento.setMessageId(messageId);
            evento.setTelefono(normalizarTelefono(text(message.path("from"))));
            evento.setTipo(text(message.path("type")));
            evento.setTexto(extraerTexto(message));
            evento.setNombrePerfil(text(value.path("contacts").path(0).path("profile").path("name")));
            asociarClienteYExpediente(evento);
            evento.setProcesado(true);
            eventoRepository.save(evento);
        } catch (Exception ex) {
            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload != null ? payload : "");
            evento.setProcesado(false);
            evento.setErrorProcesado(ex.getMessage());
            eventoRepository.save(evento);
            throw new IllegalArgumentException("No se pudo procesar el webhook de WhatsApp.", ex);
        }
    }

    @Override
    public boolean verificarToken(String token) {
        return StringUtils.hasText(verifyToken) && verifyToken.equals(token);
    }

    @Override
    public boolean firmaValida(String payload, String signature) {
        if (!StringUtils.hasText(appSecret)) {
            return true;
        }
        if (!StringUtils.hasText(signature) || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private void asociarClienteYExpediente(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        Optional<Cliente> cliente = clienteRepository.findAll().stream()
                .filter(item -> coincideTelefono(evento.getTelefono(), item.getTelefono()))
                .findFirst();
        cliente.ifPresent(evento::setCliente);
        cliente.ifPresent(item -> {
            List<Expediente> expedientes = expedienteRepository.findByClienteIdOrderByFechaReferenciaDesc(item.getId());
            if (!expedientes.isEmpty()) {
                evento.setExpediente(expedientes.get(0));
            }
        });
    }

    private boolean coincideTelefono(String origen, String telefonoCliente) {
        String cliente = normalizarTelefono(telefonoCliente);
        if (!StringUtils.hasText(cliente)) {
            return false;
        }
        return origen.endsWith(cliente) || cliente.endsWith(origen);
    }

    private String extraerTexto(JsonNode message) {
        String tipo = text(message.path("type"));
        if ("text".equals(tipo)) {
            return text(message.path("text").path("body"));
        }
        if ("button".equals(tipo)) {
            return text(message.path("button").path("text"));
        }
        if ("interactive".equals(tipo)) {
            String button = text(message.path("interactive").path("button_reply").path("title"));
            return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("title"));
        }
        return null;
    }

    private String text(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText(null) : null;
    }

    private String normalizarTelefono(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) normalized = normalized.substring(2);
        if (normalized.startsWith("34") && normalized.length() == 11) normalized = normalized.substring(2);
        return normalized;
    }
}
