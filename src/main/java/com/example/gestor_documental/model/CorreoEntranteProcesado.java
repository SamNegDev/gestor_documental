package com.example.gestor_documental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "correo_entrante_procesado")
public class CorreoEntranteProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String messageId;

    @Column(length = 500)
    private String asunto;

    @Column(length = 250)
    private String remitente;

    @Column(length = 10)
    private String matricula;

    @Column(length = 40)
    private String estado;

    @Column(length = 500)
    private String detalle;

    private Long solicitudId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaProcesado;
}
