package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.service.CorreoService;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorreoServiceImplTest {

    @Test
    void smtpIncluyeDestinatariosEnCopiaYCopiaOculta() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        CorreoServiceImpl service = new CorreoServiceImpl();
        service.setMailSender(mailSender);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "provider", "smtp");
        ReflectionTestUtils.setField(service, "from", "avisos@gestoria.test");
        ReflectionTestUtils.setField(service, "fromName", "Gestoria");

        CorreoService.ResultadoCorreo resultado = service.enviarHtml(
                "principal@cliente.test",
                "Aviso",
                "<p>Contenido</p>",
                "Contenido",
                List.of("copia1@cliente.test", "copia2@cliente.test"),
                List.of("auditoria@gestoria.test"),
                null
        );

        assertTrue(resultado.exito());
        assertEquals("principal@cliente.test", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(List.of("copia1@cliente.test", "copia2@cliente.test"),
                List.of(message.getRecipients(Message.RecipientType.CC)).stream().map(Object::toString).toList());
        assertEquals("auditoria@gestoria.test", message.getRecipients(Message.RecipientType.BCC)[0].toString());
        verify(mailSender).send(any(MimeMessage.class));
    }
}
