package com.example.gestor_documental.model;


import com.example.gestor_documental.enums.TipoPersona;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name = "interesado", indexes = {
        @Index(name = "idx_interesado_dni", columnList = "dni"),
        @Index(name = "idx_interesado_nombre", columnList = "nombre")
})
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

    @Column(length = 30)
    private String tipoVia;

    @Column(length = 120)
    private String nombreVia;

    @Column(length = 10)
    private String codigoPostal;

    @Column(length = 80)
    private String municipio;

    @Column(length = 80)
    private String provincia;

    @Column (length = 20)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPersona tipoPersona = TipoPersona.PARTICULAR;

    public Interesado(String dni, String nombre) {
        this.dni = dni;
        this.nombre = nombre;
        this.tipoPersona = TipoPersona.PARTICULAR;
    }
}


