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
@Table(name = "documento_identidad_lectura", indexes = {
        @Index(name = "idx_doc_identidad_documento", columnList = "documento_id", unique = true),
        @Index(name = "idx_doc_identidad_identificador", columnList = "identificador"),
        @Index(name = "idx_doc_identidad_interesado", columnList = "interesado_vinculado_id")
})
public class DocumentoIdentidadLectura {

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

    @Column(length = 32)
    private String identificador;

    @Column(length = 160)
    private String nombre;

    @Column(length = 160)
    private String apellido1;

    @Column(length = 160)
    private String apellido2;

    @Column(length = 220)
    private String razonSocial;

    @Column(length = 20)
    private String fechaNacimiento;

    @Column(length = 20)
    private String fechaCaducidad;

    @Column(length = 500)
    private String direccionTexto;

    @Column(length = 30)
    private String tipoVia;

    @Column(length = 120)
    private String nombreVia;

    @Column(length = 20)
    private String numeroVia;

    @Column(length = 20)
    private String bloque;

    @Column(length = 20)
    private String portal;

    @Column(length = 20)
    private String escalera;

    @Column(length = 20)
    private String piso;

    @Column(length = 20)
    private String puerta;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 80)
    private String municipio;

    @Column(length = 80)
    private String provincia;

    private Double confianzaGlobal;

    private boolean requiereRevision;

    private boolean vinculadoAutomaticamente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interesado_vinculado_id")
    private Interesado interesadoVinculado;

    @Column(length = 260)
    private String mensaje;

    @Column(length = 80)
    private String modelo;

    private LocalDateTime fechaLectura;

    @Lob
    private String resultadoJson;
}
