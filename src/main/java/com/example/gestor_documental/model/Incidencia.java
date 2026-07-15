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
@Table(name = "incidencia", indexes = {
        @Index(name = "idx_incidencia_expediente_resuelta", columnList = "expediente_id, resuelta"),
        @Index(name = "idx_incidencia_solicitud_resuelta", columnList = "solicitud_id, resuelta"),
        @Index(name = "idx_incidencia_seguimiento", columnList = "resuelta, seguimiento_archivado, proximo_aviso"),
        @Index(name = "idx_incidencia_fecha_creacion", columnList = "fecha_creacion")
})
public class Incidencia {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_incidencia_id")
    private TipoIncidencia tipoIncidencia;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false)
    private boolean resuelta;

    @Column
    private LocalDateTime fechaResolucion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resuelto_por_usuario_id")
    private Usuario resueltoPor;

    @ManyToOne(fetch = FetchType.EAGER)
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

    @Column(nullable = false)
    private int contadorAvisos = 0;

    private LocalDateTime fechaUltimoAviso;
    private LocalDateTime proximoAviso;

    @Column(nullable = false)
    private boolean seguimientoArchivado = false;

    private LocalDateTime fechaArchivoSeguimiento;

    @Column(nullable = false)
    private boolean revisionComunicadaPorCliente = false;

    private LocalDateTime fechaRevisionComunicadaPorCliente;

    @Column(length = 500)
    private String comentarioRevisionCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seguimiento_archivado_por_usuario_id")
    private Usuario seguimientoArchivadoPor;

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
