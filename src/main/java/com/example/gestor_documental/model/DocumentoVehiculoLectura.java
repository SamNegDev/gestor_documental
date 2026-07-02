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
@Table(name = "documento_vehiculo_lectura", indexes = {
        @Index(name = "idx_doc_vehiculo_documento", columnList = "documento_id", unique = true),
        @Index(name = "idx_doc_vehiculo_matricula", columnList = "matricula"),
        @Index(name = "idx_doc_vehiculo_bastidor", columnList = "bastidor")
})
public class DocumentoVehiculoLectura {

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
    private String matricula;

    @Column(length = 80)
    private String marca;

    @Column(name = "modelo_vehiculo", length = 120)
    private String modeloVehiculo;

    @Column(length = 40)
    private String bastidor;

    @Column(length = 20)
    private String fechaMatriculacion;

    @Column(length = 20)
    private String fechaPrimeraMatriculacion;

    private Double confianzaGlobal;

    private boolean requiereRevision;

    @Column(length = 300)
    private String mensaje;

    @Column(length = 80)
    private String modelo;

    private LocalDateTime fechaLectura;

    @Lob
    private String resultadoJson;
}
