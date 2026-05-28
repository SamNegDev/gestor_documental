package com.example.gestor_documental.model;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "hito_expediente",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expediente_id", "codigo"})
)
public class HitoExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private CodigoHitoExpediente codigo;

    @Column(nullable = false)
    private LocalDateTime fechaCompletado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "completado_por_usuario_id")
    private Usuario completadoPor;

    @Column(length = 300)
    private String nota;
}
