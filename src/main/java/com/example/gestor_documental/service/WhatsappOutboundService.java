package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;

import java.util.List;

public interface WhatsappOutboundService {
    ResultadoWhatsapp enviarTexto(String destinatario, String mensaje);
    ResultadoWhatsapp enviarImagen(String destinatario, String imageUrl, String caption);
    ResultadoWhatsapp enviarAvisoSeguimiento(String destinatario, String mensaje);
    ResultadoWhatsapp enviarMenuPrincipal(String destinatario);
    ResultadoWhatsapp enviarMenuContinuacion(String destinatario, Expediente expediente, String mensaje);
    ResultadoWhatsapp enviarMenuContinuacionSolicitud(String destinatario, Solicitud solicitud, String mensaje);
    ResultadoWhatsapp enviarMenuTiposSolicitud(String destinatario, List<TipoTramite> tipos);
    boolean envioRealDisponible();

    record ResultadoWhatsapp(boolean exito, boolean simulado, String messageId, String error) {
        public static ResultadoWhatsapp enviado(String messageId) {
            return new ResultadoWhatsapp(true, false, messageId, null);
        }

        public static ResultadoWhatsapp simulacion() {
            return new ResultadoWhatsapp(true, true, null, null);
        }

        public static ResultadoWhatsapp error(String error) {
            return new ResultadoWhatsapp(false, false, null, error);
        }
    }
}
