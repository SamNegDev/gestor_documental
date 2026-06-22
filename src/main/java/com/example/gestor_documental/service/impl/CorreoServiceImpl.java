package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.CorreoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import jakarta.mail.AuthenticationFailedException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CorreoServiceImpl implements CorreoService {
    private final RestClient restClient = RestClient.create();
    private JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;
    @Value("${app.mail.provider:smtp}")
    private String provider;
    @Value("${app.mail.from:}")
    private String from;
    @Value("${app.mail.from-name:Gestoria CN}")
    private String fromName;
    @Value("${app.mail.graph.tenant-id:}")
    private String graphTenantId;
    @Value("${app.mail.graph.client-id:}")
    private String graphClientId;
    @Value("${app.mail.graph.client-secret:}")
    private String graphClientSecret;
    @Value("${app.mail.graph.sender:}")
    private String graphSender;
    @Value("${app.mail.graph.save-to-sent-items:true}")
    private boolean graphSaveToSentItems;

    @Override
    public ResultadoCorreo enviar(String destinatario, String asunto, String mensaje) {
        return enviar(destinatario, asunto, mensaje, List.of());
    }

    @Override
    public ResultadoCorreo enviar(String destinatario, String asunto, String mensaje, List<String> copiaOculta) {
        if (!enabled) return ResultadoCorreo.simulacion();
        if (destinatario == null || destinatario.isBlank()) return ResultadoCorreo.error("El cliente no tiene un correo configurado.");
        if ("graph".equalsIgnoreCase(provider)) return enviarGraph(destinatario, asunto, mensaje, copiaOculta);
        if (from == null || from.isBlank()) return ResultadoCorreo.error("No se ha configurado el remitente del correo.");
        if (mailSender == null) return ResultadoCorreo.error("No se ha configurado el servicio SMTP.");
        try {
            var correo = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(correo, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(destinatario);
            String[] bcc = destinatarios(copiaOculta);
            if (bcc.length > 0) {
                helper.setBcc(bcc);
            }
            helper.setSubject(asunto);
            helper.setText(mensaje, false);
            mailSender.send(correo);
            return ResultadoCorreo.enviado();
        } catch (MailException | MessagingException | UnsupportedEncodingException | IllegalArgumentException ex) {
            return ResultadoCorreo.error(detalleError(ex));
        }
    }

    @Autowired(required = false)
    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private ResultadoCorreo enviarGraph(String destinatario, String asunto, String mensaje, List<String> copiaOculta) {
        String remitente = StringUtils.hasText(graphSender) ? graphSender.trim() : trim(from);
        if (!StringUtils.hasText(remitente)) return ResultadoCorreo.error("No se ha configurado el buzon remitente de Microsoft Graph.");
        if (!StringUtils.hasText(graphTenantId) || !StringUtils.hasText(graphClientId) || !StringUtils.hasText(graphClientSecret)) {
            return ResultadoCorreo.error("Faltan credenciales de Microsoft Graph: tenant, client id o client secret.");
        }
        try {
            String token = obtenerTokenGraph();
            Map<String, Object> message = new java.util.LinkedHashMap<>();
            message.put("subject", asunto != null ? asunto : "");
            message.put("body", Map.of(
                    "contentType", "Text",
                    "content", mensaje != null ? mensaje : ""
            ));
            message.put("toRecipients", List.of(Map.of(
                    "emailAddress", Map.of("address", destinatario.trim())
            )));
            List<Map<String, Object>> bccRecipients = destinatariosGraph(copiaOculta);
            if (!bccRecipients.isEmpty()) {
                message.put("bccRecipients", bccRecipients);
            }
            Map<String, Object> payload = Map.of(
                    "message", message,
                    "saveToSentItems", graphSaveToSentItems
            );
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("graph.microsoft.com")
                            .pathSegment("v1.0", "users", remitente, "sendMail")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return ResultadoCorreo.enviado();
        } catch (RestClientResponseException ex) {
            return ResultadoCorreo.error("Microsoft Graph ha rechazado el envio (" + ex.getStatusCode().value() + "): " + detalleGraph(ex));
        } catch (RestClientException | IllegalArgumentException ex) {
            return ResultadoCorreo.error("No se pudo enviar mediante Microsoft Graph: " + ex.getMessage());
        }
    }

    private String[] destinatarios(List<String> correos) {
        if (correos == null) {
            return new String[0];
        }
        return correos.stream()
                .map(this::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toArray(String[]::new);
    }

    private List<Map<String, Object>> destinatariosGraph(List<String> correos) {
        return java.util.Arrays.stream(destinatarios(correos))
                .map(correo -> Map.<String, Object>of("emailAddress", Map.of("address", correo)))
                .toList();
    }

    private String obtenerTokenGraph() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", graphClientId.trim());
        form.add("client_secret", graphClientSecret.trim());
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");

        Map<?, ?> response = restClient.post()
                .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", graphTenantId.trim())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object token = response != null ? response.get("access_token") : null;
        if (token == null || token.toString().isBlank()) {
            throw new RestClientException("Microsoft no devolvio access_token.");
        }
        return token.toString();
    }

    private String detalleGraph(RestClientResponseException error) {
        String body = error.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : error.getMessage();
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private String detalleError(Throwable error) {
        Throwable actual = error;
        String detalle = null;
        while (actual != null) {
            if (actual.getMessage() != null && !actual.getMessage().isBlank()) detalle = actual.getMessage();
            if (actual instanceof MessagingException messaging && messaging.getNextException() != null
                    && messaging.getNextException() != actual.getCause()) {
                String siguiente = detalleError(messaging.getNextException());
                if (siguiente != null && !siguiente.isBlank()) detalle = siguiente;
            }
            actual = actual.getCause();
        }
        if (detalle != null && detalle.contains("security defaults policy")) {
            return "Microsoft 365 ha bloqueado SMTP por la politica Security Defaults. Es necesario configurar el envio mediante OAuth/Microsoft Graph.";
        }
        if (error instanceof MailAuthenticationException || error instanceof AuthenticationFailedException
                || detalle != null && detalle.contains("Authentication unsuccessful")) {
            return "Microsoft 365 ha rechazado la autenticacion. Revisa SMTP AUTH o configura OAuth/Microsoft Graph.";
        }
        return detalle != null ? detalle : "Microsoft 365 no ha proporcionado detalles del error SMTP.";
    }
}
