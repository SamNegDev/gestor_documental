package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.TipoDocumento;
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
@Table(name = "correccion_clasificacion_documento", indexes = {
        @Index(name = "idx_corr_clas_doc_fecha", columnList = "fecha_correccion"),
        @Index(name = "idx_corr_clas_doc_tipo", columnList = "tipo_anterior, tipo_corregido"),
        @Index(name = "idx_corr_clas_doc_documento", columnList = "documento_id"),
        @Index(name = "idx_corr_clas_doc_expediente", columnList = "expediente_id"),
        @Index(name = "idx_corr_clas_doc_solicitud", columnList = "solicitud_id")
})
public class CorreccionClasificacionDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCorreccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_anterior", length = 100)
    private TipoDocumento tipoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corregido", nullable = false, length = 100)
    private TipoDocumento tipoCorregido;

    @Column(nullable = false, length = 60)
    private String origen;

    @Column(length = 200)
    private String nombreArchivoOriginal;
}
