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
@Table(name = "gestion_persona_representante_catalogo", indexes = {
        @Index(name = "idx_gestion_repr_empresa_nif", columnList = "empresaNifNormalizado"),
        @Index(name = "idx_gestion_repr_nif", columnList = "representanteNifNormalizado"),
        @Index(name = "idx_gestion_repr_empresa_codigo", columnList = "empresaCodigoColegio,empresaNumeroColegiado,empresaCodigoDespacho,empresaCodigoPersona")
})
public class GestionPersonaRepresentanteCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String empresaCodigoColegio;

    @Column(length = 20)
    private String empresaNumeroColegiado;

    @Column(length = 20)
    private String empresaCodigoDespacho;

    @Column(length = 20)
    private String empresaCodigoPersona;

    @Column(length = 40)
    private String empresaNifNormalizado;

    @Column(length = 30)
    private String empresaTipoPersonaSugerido;

    @Column(length = 40)
    private String empresaNif;

    @Column(length = 200)
    private String empresaApellido1RazonSocial;

    @Column(length = 160)
    private String empresaApellido2;

    @Column(length = 160)
    private String empresaNombre;

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

    @Column(length = 40)
    private String representanteNifNormalizado;

    @Column(length = 30)
    private String representanteTipoPersonaSugerido;

    @Column(length = 40)
    private String representanteNif;

    @Column(length = 200)
    private String representanteApellido1RazonSocial;

    @Column(length = 160)
    private String representanteApellido2;

    @Column(length = 160)
    private String representanteNombre;

    @Column(length = 20)
    private String representanteSexo;

    @Column(length = 40)
    private String representanteFechaNacimiento;

    @Column(length = 80)
    private String representanteTipoDocumentoSustitutivo;

    @Column(length = 40)
    private String representanteFechaCaducidadDocumento;

    @Column(length = 80)
    private String representanteNacionalidad;

    @Column(length = 30)
    private String representanteDirSiglas;

    @Column(length = 220)
    private String representanteDirCalle;

    @Column(length = 30)
    private String representanteDirNumero;

    @Column(length = 30)
    private String representanteDirKm;

    @Column(length = 30)
    private String representanteDirHectometro;

    @Column(length = 30)
    private String representanteDirLetra;

    @Column(length = 30)
    private String representanteDirEscalera;

    @Column(length = 30)
    private String representanteDirPiso;

    @Column(length = 30)
    private String representanteDirPuerta;

    @Column(length = 30)
    private String representanteDirBloque;

    @Column(length = 120)
    private String representanteDirMunicipio;

    @Column(length = 120)
    private String representanteDirPueblo;

    @Column(length = 40)
    private String representanteDirProvincia;

    @Column(length = 20)
    private String representanteDirCp;

    @Column(length = 40)
    private String representanteDirPais;

    @Column(nullable = false)
    private LocalDateTime fechaImportacion;
}
