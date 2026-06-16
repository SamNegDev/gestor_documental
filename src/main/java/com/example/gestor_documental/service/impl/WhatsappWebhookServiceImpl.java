package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.WhatsappMediaService;
import com.example.gestor_documental.service.WhatsappOutboundService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsappWebhookServiceImpl implements WhatsappWebhookService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappWebhookEventoRepository eventoRepository;
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final HistorialCambioService historialCambioService;
    private final WhatsappOutboundService whatsappOutboundService;
    private final WhatsappMediaService whatsappMediaService;

    @Value("${app.whatsapp.webhook.verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    @Value("${app.public-url:}")
    private String publicUrl;

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
            if (message.isMissingNode() || message.isNull()) {
                return;
            }
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
            evento.setAccionCodigo(extraerAccionCodigo(message));
            evento.setNombrePerfil(text(value.path("contacts").path(0).path("profile").path("name")));
            asociarClienteYExpediente(evento);
            boolean media = esMedia(message);
            if (media) {
                evento.setEstado(EstadoWhatsappEvento.REVISADO);
                evento.setFechaRevision(LocalDateTime.now());
            } else if (!procesarAccion(evento)) {
                procesarMenuEspontaneo(evento);
            }
            evento.setProcesado(true);
            evento = eventoRepository.save(evento);
            if (media) {
                whatsappMediaService.descargarYGuardar(evento, message);
            }
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

    private boolean procesarAccion(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getAccionCodigo())) {
            return false;
        }
        String accion = evento.getAccionCodigo();
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return true;
        }
        if ("gestapp_menu_expedientes".equals(accion)) {
            enviarEnlacePortalCliente(evento, "Puedes consultar tus expedientes desde el portal:");
            return true;
        }
        if ("gestapp_menu_pendiente".equals(accion)) {
            enviarDocumentacionPendiente(evento);
            return true;
        }
        if ("gestapp_contactar".equals(accion)) {
            return false;
        }
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes continuar desde el portal de GestApp:");
            return true;
        }
        if ("gestapp_recordar_manana".equals(accion)) {
            posponerSeguimiento(evento);
            return true;
        }
        if ("gestapp_enviar_documentacion".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes subir la documentacion desde el portal:");
            return true;
        }
        if ("gestapp_ver_expediente".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes consultar el expediente desde el portal:");
            return true;
        }
        return false;
    }

    private void procesarMenuEspontaneo(WhatsappWebhookEvento evento) {
        if (!esSolicitudMenu(evento.getTexto())) {
            return;
        }
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return;
        }
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private void posponerSeguimiento(WhatsappWebhookEvento evento) {
        Optional<Incidencia> incidencia = incidenciaRepository.findByExpedienteIdAndResueltaFalse(evento.getExpediente().getId()).stream()
                .filter(item -> !item.isSeguimientoArchivado())
                .max(Comparator.comparing(item -> item.getFechaCreacion() != null ? item.getFechaCreacion() : LocalDateTime.MIN));
        if (incidencia.isEmpty()) {
            return;
        }
        LocalDateTime proximoAviso = LocalDateTime.now().plusDays(1).with(LocalTime.of(9, 0));
        incidencia.get().setProximoAviso(proximoAviso);
        incidenciaRepository.save(incidencia.get());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "El cliente pidio recordatorio por WhatsApp. Seguimiento pospuesto hasta " + proximoAviso + ".");
    }

    private void enviarEnlacePortal(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/expedientes/" + evento.getExpediente().getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "Se envio al cliente un enlace al portal tras pulsar un boton de WhatsApp.");
    }

    private void enviarEnlacePortalCliente(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/cliente/expedientes";
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private void enviarDocumentacionPendiente(WhatsappWebhookEvento evento) {
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes revisar la documentacion pendiente desde el portal:");
            return;
        }
        List<String> pendientes = requisitoRepository.findByExpedienteIdOrderByIdAsc(evento.getExpediente().getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(this::descripcionRequisito)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        String detalle = pendientes.isEmpty()
                ? "No veo documentacion marcada como pendiente en tu ultimo expediente. Puedes revisarlo en el portal:"
                : "Documentacion pendiente en tu ultimo expediente:\n- " + String.join("\n- ", pendientes) + "\n\nPuedes aportarla desde el portal:";
        enviarEnlacePortal(evento, detalle);
    }

    private String descripcionRequisito(RequisitoDocumentalExpediente requisito) {
        if (StringUtils.hasText(requisito.getDescripcion())) {
            return requisito.getDescripcion().trim();
        }
        if (requisito.getTipoDocumento() != null) {
            return requisito.getTipoDocumento().name().replace('_', ' ');
        }
        return null;
    }

    private void enviarIdentificacionNecesaria(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Hola, soy GestApp. No he podido identificar este telefono. Responde con tu nombre y la gestoria revisara la asociacion.");
    }

    private boolean esSolicitudMenu(String texto) {
        String limpio = normalizarTexto(texto);
        if (!StringUtils.hasText(limpio)) {
            return false;
        }
        return List.of("hola", "buenas", "buenos dias", "buenas tardes", "menu", "menú", "ayuda", "inicio", "gestapp")
                .contains(limpio);
    }

    private String normalizarTexto(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
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
        if ("image".equals(tipo)) {
            String caption = text(message.path("image").path("caption"));
            return StringUtils.hasText(caption) ? caption : "Imagen recibida por WhatsApp";
        }
        if ("document".equals(tipo)) {
            String caption = text(message.path("document").path("caption"));
            String filename = text(message.path("document").path("filename"));
            if (StringUtils.hasText(caption)) {
                return caption;
            }
            return StringUtils.hasText(filename) ? filename : "Documento recibido por WhatsApp";
        }
        return null;
    }

    private boolean esMedia(JsonNode message) {
        String tipo = text(message.path("type"));
        return "image".equals(tipo) || "document".equals(tipo);
    }

    private String extraerAccionCodigo(JsonNode message) {
        if (!"interactive".equals(text(message.path("type")))) {
            return null;
        }
        String button = text(message.path("interactive").path("button_reply").path("id"));
        return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("id"));
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
