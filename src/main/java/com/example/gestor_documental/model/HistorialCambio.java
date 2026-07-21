package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.TipoActividadHistorial;
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
@Table(name = "historial_cambio", indexes = {
        @Index(name = "idx_historial_expediente_fecha", columnList = "expediente_id, fecha_cambio"),
        @Index(name = "idx_historial_solicitud_fecha", columnList = "solicitud_id, fecha_cambio"),
        @Index(name = "idx_historial_accion_fecha", columnList = "accion, fecha_cambio")
})
public class HistorialCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCambio;

    @Column(nullable = false, length = 100)
    private String accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'CAMBIO'")
    private TipoActividadHistorial tipoActividad = TipoActividadHistorial.CAMBIO;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;


    public TipoActividadHistorial getTipoActividad() {
        if (tipoActividad == TipoActividadHistorial.COMUNICACION) {
            return tipoActividad;
        }
        return "AVISO INCIDENCIA".equals(accion)
                || "AVISO PENDIENTE".equals(accion)
                || "LISTADO INCIDENCIAS".equals(accion)
                || "SEGUIMIENTO POSPUESTO".equals(accion)
                ? TipoActividadHistorial.COMUNICACION
                : TipoActividadHistorial.CAMBIO;
    }
    public HistorialCambio(String accion, String descripcion, Expediente expediente, Solicitud solicitud, Usuario usuario) {
        this.accion = accion;
        this.descripcion = descripcion;
        this.expediente = expediente;
        this.solicitud = solicitud;
        this.usuario = usuario;
    }
}
