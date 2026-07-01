package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoExtraccionGaJob;
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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "extraccion_ga_job", indexes = {
        @Index(name = "idx_extraccion_ga_job_expediente", columnList = "expediente_id"),
        @Index(name = "idx_extraccion_ga_job_estado_fecha", columnList = "estado, fecha_creacion"),
        @Index(name = "idx_extraccion_ga_job_creado_por", columnList = "creado_por_id")
})
public class ExtraccionGaJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id")
    private ExtraccionGaRevision revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoExtraccionGaJob estado = EstadoExtraccionGaJob.PENDIENTE;

    @Column(length = 80)
    private String modelo;

    @Column(nullable = false)
    private Integer progreso = 0;

    @Column(length = 160)
    private String faseActual;

    @Column(length = 1000)
    private String mensajeError;

    @Column
    private Boolean usoCliente = false;

    @Column(length = 40)
    private String origen;

    @Column(nullable = false)
    private Integer intentos = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaInicio;

    private LocalDateTime fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;
}
