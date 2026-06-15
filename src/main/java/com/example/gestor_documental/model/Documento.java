package com.example.gestor_documental.model;


import com.example.gestor_documental.enums.TipoDocumento;
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
@Table(name = "documento", indexes = {
        @Index(name = "idx_documento_expediente_tipo", columnList = "expediente_id, tipo_documento"),
        @Index(name = "idx_documento_solicitud_tipo", columnList = "solicitud_id, tipo_documento"),
        @Index(name = "idx_documento_cliente_tipo", columnList = "cliente_id, tipo_documento"),
        @Index(name = "idx_documento_cliente_interesado", columnList = "cliente_id, interesado_id"),
        @Index(name = "idx_documento_incidencia", columnList = "incidencia_id"),
        @Index(name = "idx_documento_fecha_subida", columnList = "fecha_subida")
})
public class Documento {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaSubida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private TipoDocumento tipoDocumento;

    @Column(length = 200)
    private String nombreArchivo;

    @Column(nullable = false, length = 200)
    private String nombreArchivoOriginal;

    @Column(length = 200)
    private String descripcionArchivo;

    // Un documento puede estar asociado a una solicitud, a un expediente o a ambos,
    // según el flujo de negocio.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interesado_id")
    private Interesado interesado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incidencia_id")
    private Incidencia incidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacion_id")
    private OperacionExpediente operacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subido_por_usuario_id")
    private Usuario subidoPor;




}
