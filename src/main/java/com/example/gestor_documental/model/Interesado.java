package com.example.gestor_documental.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name="interesado")
public class Interesado {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (nullable = false,length = 20)
    private String dni;

    @Column (nullable = false, length = 120)
    private String nombre;

    @Column (length = 200)
    private String direccion;

    @Column (length = 20)
    private String telefono;

    public Interesado(String dni, String nombre) {
        this.dni = dni;
        this.nombre = nombre;
    }
}


