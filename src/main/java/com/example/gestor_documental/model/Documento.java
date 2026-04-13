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
@Table (name="documento")
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
    @JoinColumn(name = "subido_por_usuario_id")
    private Usuario subidoPor;




}
