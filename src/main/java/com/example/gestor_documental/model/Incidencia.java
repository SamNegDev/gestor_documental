package com.example.gestor_documental.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor

@Entity
@Table (name= "incidencia")
public class Incidencia {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_incidencia_id")
    private TipoIncidencia tipoIncidencia;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false)
    private boolean resuelta;

    @Column
    private LocalDateTime fechaResolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por_usuario_id")
    private Usuario resueltoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @Column(length = 500)
    private String observaciones;

    public Incidencia(TipoIncidencia tipoIncidencia, Expediente expediente, String observaciones, Usuario creadoPor) {
        this.tipoIncidencia = tipoIncidencia;
        this.expediente = expediente;
        this.observaciones = observaciones;
        this.creadoPor = creadoPor;
        this.resuelta = false;
    }

    public Incidencia(TipoIncidencia tipoIncidencia, Solicitud solicitud, String observaciones, Usuario creadoPor) {
        this.tipoIncidencia = tipoIncidencia;
        this.solicitud = solicitud;
        this.observaciones = observaciones;
        this.creadoPor = creadoPor;
        this.resuelta = false;
    }
}
