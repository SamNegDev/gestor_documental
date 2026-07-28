package com.example.gestor_documental.model;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="holded_webhook_evento") public class HoldedWebhookEvento {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="event_id",nullable=false,unique=true,length=150) private String eventId;
 @Column(name="recibido_en",nullable=false) private LocalDateTime recibidoEn; @Column(name="procesado",nullable=false) private boolean procesado;
}
