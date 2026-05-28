package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.EstadoOperacionExpediente;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "operacion_expediente",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expediente_id", "tipo"})
)
public class OperacionExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private TipoOperacionExpediente tipo;

    @Column(nullable = false)
    private int orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOperacionExpediente estado = EstadoOperacionExpediente.PENDIENTE;

    @Column(length = 220)
    private String descripcion;
}
