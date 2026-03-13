package com.example.gestor_documental.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Mediante el uso de Lombrok generamos los getters, setters y constructor vacío
@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name= "tipo_incidencia")
public class TipoIncidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo;
}
