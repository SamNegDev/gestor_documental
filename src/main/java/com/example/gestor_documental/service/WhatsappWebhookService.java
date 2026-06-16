package com.example.gestor_documental.service;

public interface WhatsappWebhookService {
    void procesar(String payload, String signature);
    boolean verificarToken(String token);
    boolean firmaValida(String payload, String signature);
}
