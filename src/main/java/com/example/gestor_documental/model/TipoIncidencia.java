package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.TipoIncidenciaEnum;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, unique = true)
    private TipoIncidenciaEnum nombre;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo;

    public TipoIncidencia(TipoIncidenciaEnum nombre, String descripcion, boolean activo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }
}
