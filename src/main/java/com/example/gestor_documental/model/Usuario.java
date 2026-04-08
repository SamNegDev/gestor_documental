package com.example.gestor_documental.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.gestor_documental.enums.RolUsuario;


import java.util.ArrayList;
import java.util.List;

//Mediante el uso de Lombrok generamos los getters, setters y constructor vacío
    @Getter
    @Setter
    @NoArgsConstructor

    @Entity
    @Table(name = "usuario")
    public class Usuario {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, length = 100)
        private String nombre;

        @Column(nullable = false, length = 100)
        private String apellidos;

        @Column(nullable = false, unique = true, length = 150)
        private String email;

        @Column(nullable = false, length = 255)
        private String password;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private RolUsuario rolUsuario;

        @Column(nullable = false)
        private boolean activo;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "cliente_id")
        private Cliente cliente;

        @OneToMany(mappedBy = "creadoPor", fetch = FetchType.LAZY)
        private List<Solicitud> solicitudes = new ArrayList<>();



        public Usuario(String nombre, String apellidos, String email, String password, RolUsuario rolUsuario, boolean activo) {
            this.nombre = nombre;
            this.apellidos = apellidos;
            this.email = email;
            this.password = password;
            this.rolUsuario = rolUsuario;
            this.activo = activo;
        }

    }

