package com.example.gestor_documental.repository;
import com.example.gestor_documental.model.HoldedWebhookEvento;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HoldedWebhookEventoRepository extends JpaRepository<HoldedWebhookEvento, Long> { boolean existsByEventId(String eventId); }
