package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.CorreoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import jakarta.mail.AuthenticationFailedException;

@Service
@RequiredArgsConstructor
public class CorreoServiceImpl implements CorreoService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;
    @Value("${app.mail.from:}")
    private String from;
    @Value("${app.mail.from-name:Gestoria CN}")
    private String fromName;

    @Override
    public ResultadoCorreo enviar(String destinatario, String asunto, String mensaje) {
        if (!enabled) return ResultadoCorreo.simulacion();
        if (destinatario == null || destinatario.isBlank()) return ResultadoCorreo.error("El cliente no tiene un correo configurado.");
        if (from == null || from.isBlank()) return ResultadoCorreo.error("No se ha configurado el remitente del correo.");
        try {
            var correo = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(correo, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(mensaje, false);
            mailSender.send(correo);
            return ResultadoCorreo.enviado();
        } catch (MailException | MessagingException | UnsupportedEncodingException | IllegalArgumentException ex) {
            return ResultadoCorreo.error(detalleError(ex));
        }
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
