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
@Table(name = "gestion_vehiculo_catalogo", indexes = {
        @Index(name = "idx_gestion_vehiculo_matricula", columnList = "matriculaNormalizada"),
        @Index(name = "idx_gestion_vehiculo_bastidor", columnList = "bastidorNormalizado"),
        @Index(name = "idx_gestion_vehiculo_marca_modelo", columnList = "marca,modeloSugerido")
})
public class GestionVehiculoCatalogo {

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
    private String codigoVehiculo;

    @Column(length = 20)
    private String matriculaNormalizada;

    @Column(length = 60)
    private String bastidorNormalizado;

    @Column(length = 30)
    private String matricula;

    @Column(length = 60)
    private String bastidor;

    @Column(length = 160)
    private String marca;

    @Column(length = 240)
    private String modeloSugerido;

    @Column(length = 240)
    private String modeloTransmision;

    @Column(length = 240)
    private String modeloMatriculacion;

    @Column(length = 160)
    private String tipo;

    @Column(length = 160)
    private String version;

    @Column(length = 160)
    private String marcaBase;

    @Column(length = 160)
    private String tipoBase;

    @Column(length = 160)
    private String versionBase;

    @Column(length = 40)
    private String fechaMatriculacion;

    @Column(length = 40)
    private String fechaPrimeraMatriculacion;

    @Column(length = 20)
    private String anyoFabricacion;

    @Column(length = 20)
    private String carburanteCodigo;

    @Column(length = 120)
    private String carburanteDescripcion;

    @Column(length = 20)
    private String carburanteCodigoMate;

    @Column(length = 120)
    private String tipoAlimentacion;

    @Column(length = 80)
    private String clasificacionItv;

    @Column(length = 80)
    private String codigoItv;

    @Column(length = 40)
    private String codigoTrafTipoVehiculo;

    @Column(length = 160)
    private String trafTipoDescripcion;

    @Column(length = 40)
    private String codigo620TipoVehiculo;

    @Column(length = 160)
    private String tipo620Descripcion;

    @Column(length = 20)
    private String tipo620Tt;

    @Column(length = 40)
    private String potencia;

    @Column(length = 40)
    private String cilindrada;

    @Column(length = 40)
    private String numeroCilindros;

    @Column(length = 40)
    private String masa;

    @Column(length = 40)
    private String tara;

    @Column(length = 40)
    private String plazas;

    @Column(length = 40)
    private String fechaItv;

    @Column(length = 20)
    private String historicoSn;

    @Column(length = 20)
    private String renting;

    @Column(length = 40)
    private String fechaUltModif;

    @Column(length = 20)
    private String completitudScore;

    @Column(length = 20)
    private String duplicadoMatricula;

    @Column(length = 20)
    private String preferenteMatricula;

    @Column(nullable = false)
    private LocalDateTime fechaImportacion;
}
