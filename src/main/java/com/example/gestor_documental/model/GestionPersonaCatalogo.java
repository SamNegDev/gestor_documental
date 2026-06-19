package com.example.gestor_documental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gestion_persona_catalogo", indexes = {
        @Index(name = "idx_gestion_persona_nif", columnList = "nifNormalizado"),
        @Index(name = "idx_gestion_persona_nombre", columnList = "apellido1RazonSocial,nombre"),
        @Index(name = "idx_gestion_persona_codigo", columnList = "codigoColegio,numeroColegiado,codigoDespacho,codigoPersona")
})
public class GestionPersonaCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String codigoColegio;

    @Column(length = 20)
    private String numeroColegiado;

    @Column(length = 20)
    private String codigoDespacho;

    @Column(length = 20)
    private String codigoPersona;

    @Column(length = 40)
    private String nifNormalizado;

    @Column(length = 30)
    private String tipoPersonaSugerido;

    @Column(length = 40)
    private String nif;

    @Column(length = 200)
    private String apellido1RazonSocial;

    @Column(length = 160)
    private String apellido2;

    @Column(length = 160)
    private String nombre;

    @Column(length = 20)
    private String sexo;

    @Column(length = 40)
    private String fechaNacimiento;

    @Column(length = 40)
    private String autonomoSn;

    @Column(length = 80)
    private String telefono;

    @Column(length = 80)
    private String telefonoMovil;

    @Column(length = 80)
    private String telefono2;

    @Column(length = 200)
    private String email;

    @Column(length = 200)
    private String emailFacturacion;

    @Column(length = 200)
    private String emailNotificaciones;

    @Column(length = 80)
    private String tipoDocumentoSustitutivo;

    @Column(length = 40)
    private String fechaCaducidadDocumento;

    @Column(length = 80)
    private String nacionalidad;

    @Column(length = 40)
    private String mandatoFecha;

    @Column(length = 120)
    private String mandatoReferencia;

    @Column(length = 20)
    private String mandatoPrimeraVezSn;

    @Column(length = 30)
    private String dirSiglas;

    @Column(length = 220)
    private String dirCalle;

    @Column(length = 30)
    private String dirNumero;

    @Column(length = 30)
    private String dirKm;

    @Column(length = 30)
    private String dirHectometro;

    @Column(length = 30)
    private String dirLetra;

    @Column(length = 30)
    private String dirEscalera;

    @Column(length = 30)
    private String dirPiso;

    @Column(length = 30)
    private String dirPuerta;

    @Column(length = 30)
    private String dirBloque;

    @Column(length = 120)
    private String dirMunicipio;

    @Column(length = 120)
    private String dirPueblo;

    @Column(length = 40)
    private String dirProvincia;

    @Column(length = 20)
    private String dirCp;

    @Column(length = 40)
    private String dirPais;

    @Column(length = 20)
    private String reprCodigoColegio;

    @Column(length = 20)
    private String reprNumeroColegiado;

    @Column(length = 20)
    private String reprCodigoDespacho;

    @Column(length = 20)
    private String reprCodigoPersona;

    @Column(length = 120)
    private String reprConcepto;

    @Column(length = 120)
    private String reprDocAcreditacion;

    @Column(nullable = false)
    private LocalDateTime fechaImportacion;
}
