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
}
