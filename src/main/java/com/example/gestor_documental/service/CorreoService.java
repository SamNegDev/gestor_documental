package com.example.gestor_documental.service;

public interface CorreoService {
    ResultadoCorreo enviar(String destinatario, String asunto, String mensaje);

    record ResultadoCorreo(boolean exito, boolean simulado, String error) {
        public static ResultadoCorreo enviado() { return new ResultadoCorreo(true, false, null); }
        public static ResultadoCorreo simulacion() { return new ResultadoCorreo(true, true, null); }
        public static ResultadoCorreo error(String error) { return new ResultadoCorreo(false, false, error); }
    }
}
