package com.example.gestor_documental.service;

import java.util.List;

public interface CorreoService {
    ResultadoCorreo enviar(String destinatario, String asunto, String mensaje);
    ResultadoCorreo enviar(String destinatario, String asunto, String mensaje, List<String> copiaOculta);
    ResultadoCorreo enviarHtml(String destinatario, String asunto, String html, String textoAlternativo, List<String> copiaOculta);

    record ResultadoCorreo(boolean exito, boolean simulado, String error) {
        public static ResultadoCorreo enviado() { return new ResultadoCorreo(true, false, null); }
        public static ResultadoCorreo simulacion() { return new ResultadoCorreo(true, true, null); }
        public static ResultadoCorreo error(String error) { return new ResultadoCorreo(false, false, error); }
    }
}
