package com.example.gestor_documental.model;
import jakarta.persistence.*;

    @Entity
    @Table(name = "usuarios")
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

        @Column(nullable = false, length = 20)
        private String rol;

        @Column(nullable = false)
        private boolean activo;

        public Usuario() {
        }

        public Usuario(String nombre, String apellidos, String email, String password, String rol, boolean activo) {
            this.nombre = nombre;
            this.apellidos = apellidos;
            this.email = email;
            this.password = password;
            this.rol = rol;
            this.activo = activo;
        }

        public Long getId() {
            return id;
        }

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getApellidos() {
            return apellidos;
        }

        public void setApellidos(String apellidos) {
            this.apellidos = apellidos;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRol() {
            return rol;
        }

        public void setRol(String rol) {
            this.rol = rol;
        }
        public boolean isActivo() {
            return activo;
        }

        public void setActivo(boolean activo) {
            this.activo = activo;
        }
    }

