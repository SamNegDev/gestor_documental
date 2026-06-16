package com.example.gestor_documental.service;

public interface WhatsappOutboundService {
    ResultadoWhatsapp enviarTexto(String destinatario, String mensaje);
    ResultadoWhatsapp enviarAvisoSeguimiento(String destinatario, String mensaje);
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
