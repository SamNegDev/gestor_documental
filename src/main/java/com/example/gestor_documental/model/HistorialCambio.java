package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CategoriaHistorial categoria;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AudienciaHistorial audiencia;

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

    @PrePersist
    void clasificarAntesDeGuardar() {
        if (categoria == null) categoria = inferirCategoria();
        if (audiencia == null) audiencia = inferirAudiencia(categoria);
    }

    @Transient
    public CategoriaHistorial getCategoriaClasificada() {
        return categoria != null ? categoria : inferirCategoria();
    }

    @Transient
    public AudienciaHistorial getAudienciaClasificada() {
        return audiencia != null ? audiencia : inferirAudiencia(getCategoriaClasificada());
    }

    private CategoriaHistorial inferirCategoria() {
        String valor = accion != null ? accion.toUpperCase() : "";
        if (getTipoActividad() == TipoActividadHistorial.COMUNICACION) return CategoriaHistorial.COMUNICACION;
        if (valor.contains("DOCUMENT")) return CategoriaHistorial.DOCUMENTO;
        if (valor.contains("INCIDENCIA")) return CategoriaHistorial.INCIDENCIA;
        if (valor.contains("ESTADO") || valor.contains("FINALIZ")) return CategoriaHistorial.ESTADO;
        if (valor.contains("HITO") || valor.contains("TRAMITE")) return CategoriaHistorial.TRAMITE;
        return CategoriaHistorial.SISTEMA;
    }

    private AudienciaHistorial inferirAudiencia(CategoriaHistorial categoriaCalculada) {
        String valor = accion != null ? accion.toUpperCase() : "";
        if (categoriaCalculada == CategoriaHistorial.COMUNICACION
                || categoriaCalculada == CategoriaHistorial.ESTADO
                || categoriaCalculada == CategoriaHistorial.INCIDENCIA) {
            return AudienciaHistorial.AMBOS;
        }
        if (categoriaCalculada == CategoriaHistorial.DOCUMENTO
                && !valor.contains("ELIMIN") && !valor.contains("DESCARG") && !valor.contains("CONSULT")) {
            return AudienciaHistorial.AMBOS;
        }
        return AudienciaHistorial.INTERNO;
    }
    public HistorialCambio(String accion, String descripcion, Expediente expediente, Solicitud solicitud, Usuario usuario) {
        this.accion = accion;
        this.descripcion = descripcion;
        this.expediente = expediente;
        this.solicitud = solicitud;
        this.usuario = usuario;
    }
}
