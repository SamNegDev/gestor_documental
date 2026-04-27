package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.RolInteresado;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name = "expediente_interesado")
public class ExpedienteInteresado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "interesado_id")
    private Interesado interesado;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RolInteresado rol;

    public ExpedienteInteresado(Expediente expediente, Interesado interesado, RolInteresado rol) {
        this.expediente = expediente;
        this.interesado = interesado;
        this.rol = rol;
    }
}
