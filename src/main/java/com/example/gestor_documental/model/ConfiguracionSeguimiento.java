package com.example.gestor_documental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "configuracion_seguimiento")
public class ConfiguracionSeguimiento {
    public static final Long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    @Column(nullable = false)
    private int diasAviso1 = 7;

    @Column(nullable = false)
    private int diasAviso2 = 7;

    @Column(nullable = false)
    private int diasAviso3 = 7;

    @Column(nullable = false)
    private int diasAviso4 = 30;

    @Column(nullable = false)
    private int diasAviso5 = 60;

    @Column(nullable = false)
    private int maxAvisos = 5;

    @Column(nullable = false)
    private int diasExpedienteEstancado = 7;

    @Column(nullable = false, columnDefinition = "int default 2")
    private int diasPrimerAviso = 2;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean automatizacionActiva = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean modoSupervisado = true;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'LABORABLES'")
    private String diasEnvio = "LABORABLES";

    @Column(nullable = false, columnDefinition = "int default 9")
    private int horaEnvio = 9;

    @Column(nullable = false, columnDefinition = "int default 50")
    private int tamanioLote = 50;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'EMAIL'")
    private String canalAutomatico = "EMAIL";
}
