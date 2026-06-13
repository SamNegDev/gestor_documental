package com.example.gestor_documental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehiculo", uniqueConstraints = @UniqueConstraint(name = "uk_vehiculo_matricula", columnNames = "matricula"))
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String matricula;

    @Column(length = 30)
    private String bastidor;

    @Column(length = 80)
    private String marca;

    @Column(length = 120)
    private String modelo;

    private LocalDate fechaPrimeraMatriculacion;

    @Column(length = 500)
    private String observaciones;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    private LocalDateTime fechaUltimaModificacion;
}
