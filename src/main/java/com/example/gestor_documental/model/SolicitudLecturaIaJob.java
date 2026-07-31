package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoLecturaIaJob;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "solicitud_lectura_ia_job", indexes = {
        @Index(name = "idx_sol_lectura_ia_job_solicitud_fecha", columnList = "solicitud_id, fecha_creacion"),
        @Index(name = "idx_sol_lectura_ia_job_estado_fecha", columnList = "estado, fecha_creacion")
})
public class SolicitudLecturaIaJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLecturaIaJob estado = EstadoLecturaIaJob.PENDIENTE;

    @Column(nullable = false, length = 40)
    private String origen;

    @Column(nullable = false)
    private boolean forzarRelectura;

    @Column(nullable = false)
    private int totalItems;

    @Column(nullable = false)
    private int itemsProcesados;

    @Column(nullable = false)
    private int itemsRevision;

    @Column(nullable = false)
    private int itemsError;

    @Column(nullable = false)
    private int progreso;

    @Column(length = 160)
    private String faseActual;

    @Column(length = 1000)
    private String mensaje;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SolicitudLecturaIaItem> items = new ArrayList<>();

    public void addItem(SolicitudLecturaIaItem item) {
        items.add(item);
        item.setJob(this);
    }
}
