package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoRevisionGa;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "extraccion_ga_revision", indexes = {
        @Index(name = "idx_extraccion_ga_revision_expediente", columnList = "expediente_id"),
        @Index(name = "idx_extraccion_ga_revision_estado", columnList = "estado"),
        @Index(name = "idx_extraccion_ga_revision_preparado", columnList = "fecha_preparado")
})
public class ExtraccionGaRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false, unique = true)
    private Expediente expediente;

    @Column(columnDefinition = "LONGTEXT")
    private String resultadoIaJson;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String datosValidadosJson;

    @Column(length = 80)
    private String modelo;

    private Double confianzaGlobal;

    @Column(nullable = false)
    private boolean requiereRevisionHumana;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoRevisionGa estado = EstadoRevisionGa.BORRADOR;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    private LocalDateTime fechaUltimaModificacion;

    private LocalDateTime fechaPreparado;

    private LocalDateTime fechaExportado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exportado_por_id")
    private Usuario exportadoPor;
}
