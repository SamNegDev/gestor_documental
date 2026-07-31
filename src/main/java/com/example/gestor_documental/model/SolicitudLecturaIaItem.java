package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoLecturaIaItem;
import com.example.gestor_documental.enums.TipoLecturaIa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "solicitud_lectura_ia_item", indexes = {
        @Index(name = "idx_sol_lectura_ia_item_job", columnList = "job_id"),
        @Index(name = "idx_sol_lectura_ia_item_documento_fecha", columnList = "documento_id, fecha_inicio"),
        @Index(name = "idx_sol_lectura_ia_item_estado", columnList = "estado")
})
public class SolicitudLecturaIaItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private SolicitudLecturaIaJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoLecturaIa tipoLectura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLecturaIaItem estado = EstadoLecturaIaItem.PENDIENTE;

    @Column(nullable = false)
    private int intentos;

    @Column(length = 80)
    private String modelo;

    @Column(length = 40)
    private String versionPrompt;

    private Double confianza;

    @Column(length = 1000)
    private String mensaje;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Long duracionMs;
}
