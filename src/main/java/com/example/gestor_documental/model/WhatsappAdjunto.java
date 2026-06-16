package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoWhatsappAdjunto;
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
@Table(name = "whatsapp_adjunto", indexes = {
        @Index(name = "idx_whatsapp_adjunto_media_id", columnList = "media_id"),
        @Index(name = "idx_whatsapp_adjunto_estado", columnList = "estado"),
        @Index(name = "idx_whatsapp_adjunto_fecha", columnList = "fecha_recepcion")
})
public class WhatsappAdjunto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", length = 160, unique = true)
    private String mediaId;

    @Column(length = 40)
    private String telefono;

    @Column(length = 40)
    private String tipo;

    @Column(length = 120)
    private String mimeType;

    @Column(length = 200)
    private String nombreArchivoOriginal;

    @Column(length = 240)
    private String nombreArchivo;

    @Column(length = 120)
    private String sha256;

    private Long tamanioBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, columnDefinition = "varchar(40) default 'PENDIENTE_CLASIFICAR'")
    private EstadoWhatsappAdjunto estado = EstadoWhatsappAdjunto.PENDIENTE_CLASIFICAR;

    @Column(length = 500)
    private String errorDescarga;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaRecepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id")
    private WhatsappWebhookEvento evento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;
}
