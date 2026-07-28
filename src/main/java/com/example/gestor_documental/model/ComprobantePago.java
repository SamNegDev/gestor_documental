package com.example.gestor_documental.model;
import com.example.gestor_documental.enums.EstadoComprobantePago;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="comprobante_pago",indexes=@Index(name="idx_comprobante_factura_estado",columnList="factura_id, estado"))
public class ComprobantePago {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="factura_id",nullable=false) private FacturaHolded factura;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="aportado_por_usuario_id",nullable=false) private Usuario aportadoPor;
 @Column(name="nombre_archivo",nullable=false,length=500) private String nombreArchivo; @Column(name="nombre_original",nullable=false,length=255) private String nombreOriginal;
 @Column(name="content_type",nullable=false,length=100) private String contentType; @Column(name="tamano",nullable=false) private long tamano;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoComprobantePago estado=EstadoComprobantePago.PENDIENTE_VERIFICACION;
 @Column(length=500) private String observaciones; @CreationTimestamp @Column(name="creado_en",nullable=false,updatable=false) private LocalDateTime creadoEn; @Column(name="revisado_en") private LocalDateTime revisadoEn;
}
