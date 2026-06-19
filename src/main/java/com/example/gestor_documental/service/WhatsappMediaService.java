package com.example.gestor_documental.service;

import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.fasterxml.jackson.databind.JsonNode;

public interface WhatsappMediaService {
    boolean descargarYGuardar(WhatsappWebhookEvento evento, JsonNode message);
}
