package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoVinculacionFactura;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "factura_expediente",
       uniqueConstraints = @UniqueConstraint(name = "uk_factura_expediente_expediente", columnNames = "expediente_id"),
       indexes = @Index(name = "idx_factura_expediente_factura", columnList = "factura_id, estado"))
public class FacturaExpediente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaHolded factura;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoVinculacionFactura estado = EstadoVinculacionFactura.PROPUESTA;

    @Column(name = "matricula_detectada", length = 15)
    private String matriculaDetectada;

    @Column(name = "bastidor_detectado", length = 40)
    private String bastidorDetectado;

    @Column(name = "comprador_identificador_detectado", length = 30)
    private String compradorIdentificadorDetectado;

    @Column(name = "confianza", nullable = false)
    private int confianza;

    @Column(name = "motivo_revision", length = 500)
    private String motivoRevision;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;
}