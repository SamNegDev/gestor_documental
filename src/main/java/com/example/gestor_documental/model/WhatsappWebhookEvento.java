package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "whatsapp_webhook_evento", indexes = {
        @Index(name = "idx_whatsapp_evento_message_id", columnList = "message_id"),
        @Index(name = "idx_whatsapp_evento_telefono", columnList = "telefono"),
        @Index(name = "idx_whatsapp_evento_fecha", columnList = "fecha_recepcion")
})
public class WhatsappWebhookEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", length = 160, unique = true)
    private String messageId;

    @Column(length = 40)
    private String telefono;

    @Column(length = 120)
    private String nombrePerfil;

    @Column(length = 40)
    private String tipo;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private boolean procesado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'PENDIENTE'")
    private EstadoWhatsappEvento estado = EstadoWhatsappEvento.PENDIENTE;

    private LocalDateTime fechaRevision;

    @Column(length = 500)
    private String errorProcesado;

    @CreationTimestamp
    @Column(name = "fecha_recepcion", nullable = false, updatable = false)
    private LocalDateTime fechaRecepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;
}
