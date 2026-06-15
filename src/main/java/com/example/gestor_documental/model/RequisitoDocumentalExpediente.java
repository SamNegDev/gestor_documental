package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.OrigenRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
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
@Table(name = "requisito_documental_expediente", indexes = {
        @Index(name = "idx_requisito_expediente_estado", columnList = "expediente_id, estado"),
        @Index(name = "idx_requisito_documento", columnList = "documento_id"),
        @Index(name = "idx_requisito_interesado_rol", columnList = "interesado_id, rol_interesado")
})
public class RequisitoDocumentalExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private TipoDocumento tipoDocumento;

    @Column(nullable = false, length = 180)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoRequisitoDocumental estado = EstadoRequisitoDocumental.REQUERIDO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenRequisitoDocumental origen = OrigenRequisitoDocumental.REGLA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interesado_id")
    private Interesado interesado;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RolInteresado rolInteresado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @Column(length = 300)
    private String motivoOmision;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaResolucion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resuelto_por_usuario_id")
    private Usuario resueltoPor;
}
