package com.example.gestor_documental.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table (name="cliente")


public class Cliente {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY )
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String nif;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 250, unique = true)
    private String email;

    @Column(length = 200)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @OneToMany(mappedBy = "cliente", fetch = FetchType.LAZY)
    private List<Usuario> usuarios = new ArrayList<>();


    public Cliente(String nif, String nombre, String email) {
        this.nif = nif;
        this.nombre = nombre;
        this.email = email;

    }

}
