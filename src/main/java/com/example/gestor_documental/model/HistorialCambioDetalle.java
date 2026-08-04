package com.example.gestor_documental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "historial_cambio_detalle", indexes = {
        @Index(name = "idx_historial_detalle_cambio", columnList = "historial_cambio_id")
})
public class HistorialCambioDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "historial_cambio_id", nullable = false)
    private HistorialCambio historialCambio;

    @Column(nullable = false, length = 100)
    private String campo;

    @Column(nullable = false, length = 120)
    private String etiqueta;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_posterior", columnDefinition = "TEXT")
    private String valorPosterior;
}
