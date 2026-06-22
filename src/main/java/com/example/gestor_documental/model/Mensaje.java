package com.example.gestor_documental.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes", indexes = {
        @Index(name = "idx_mensaje_expediente_fecha", columnList = "expediente_id, fecha_creacion"),
        @Index(name = "idx_mensaje_solicitud_fecha", columnList = "solicitud_id, fecha_creacion"),
        @Index(name = "idx_mensaje_autor_fecha", columnList = "autor_id, fecha_creacion")
})
@Getter
@Setter
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenido;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaLecturaAdmin;

    private LocalDateTime fechaLecturaCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;
}
