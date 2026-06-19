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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documento_roles_lectura", indexes = {
        @Index(name = "idx_doc_roles_documento", columnList = "documento_id", unique = true),
        @Index(name = "idx_doc_roles_vendedor_id", columnList = "vendedor_identificador"),
        @Index(name = "idx_doc_roles_comprador_id", columnList = "comprador_identificador")
})
public class DocumentoRolesLectura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Documento documento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento_detectado", length = 40)
    private TipoDocumento tipoDocumentoDetectado;

    @Column(length = 20)
    private String fechaDocumento;

    @Column(length = 20)
    private String matricula;

    @Column(length = 40)
    private String bastidor;

    @Column(length = 30)
    private String valorDeclarado;

    @Column(length = 32)
    private String vendedorIdentificador;

    @Column(length = 220)
    private String vendedorNombre;

    @Column(length = 500)
    private String vendedorDireccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_interesado_id")
    private Interesado vendedorInteresado;

    @Column(length = 32)
    private String compradorIdentificador;

    @Column(length = 220)
    private String compradorNombre;

    @Column(length = 500)
    private String compradorDireccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprador_interesado_id")
    private Interesado compradorInteresado;

    private Double confianzaGlobal;

    private boolean requiereRevision;

    private boolean aplicadoExpediente;

    private LocalDateTime fechaAplicacion;

    @Column(length = 300)
    private String mensaje;

    @Column(length = 80)
    private String modelo;

    private LocalDateTime fechaLectura;

    @Lob
    private String resultadoJson;
}
