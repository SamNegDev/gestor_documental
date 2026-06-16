package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.WhatsappWebhookEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappWebhookEventoRepository extends JpaRepository<WhatsappWebhookEvento, Long> {
    boolean existsByMessageId(String messageId);
}
