package com.example.gestor_documental.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "aviso_incidencia")
@Getter @Setter @NoArgsConstructor
public class AvisoIncidencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "incidencia_id", nullable = false)
    private Incidencia incidencia;
    @Column(nullable = false) private int numeroAviso;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime fechaEnvio;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "enviado_por_usuario_id", nullable = false)
    private Usuario enviadoPor;
    @Column(columnDefinition = "TEXT") private String mensaje;
    @Column(length = 250) private String destinatario;
    @Column(length = 250) private String asunto;
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'EMAIL'")
    private String canal = "EMAIL";
    @Column(nullable = false, length = 20) private String estadoEnvio;
    @Column(length = 1000) private String errorEnvio;
}
