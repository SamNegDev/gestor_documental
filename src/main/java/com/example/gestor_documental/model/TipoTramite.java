package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.TipoTramiteEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Mediante el uso de Lombrok generamos los getters, setters y constructor vacío
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="tipo_tramite")
public class TipoTramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, unique = true)
    private TipoTramiteEnum nombre;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo;



}
