package com.example.gestor_documental.model;
import com.example.gestor_documental.enums.EstadoJustificanteProvisional; import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="justificante_provisional") public class JustificanteProvisional {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="solicitud_id",nullable=false,unique=true) private Solicitud solicitud;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoJustificanteProvisional estado=EstadoJustificanteProvisional.NO_SOLICITADO;
 @Column(name="nombre_archivo",length=500) private String nombreArchivo; @Column(name="nombre_original",length=255) private String nombreOriginal;
 @Column(name="solicitado_en") private LocalDateTime solicitadoEn; @Column(name="actualizado_en",nullable=false) private LocalDateTime actualizadoEn;
}
